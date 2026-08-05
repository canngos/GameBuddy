package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.AgeBand;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.match.application.mapper.ChatMapper;
import com.gamebuddy.match.domain.client.PredictClient;
import com.gamebuddy.match.domain.event.RecommendationServedEvent;
import com.gamebuddy.match.domain.event.RecommendationServedEvent.ServedCandidate;
import com.gamebuddy.match.infrastructure.entity.*;
import com.gamebuddy.match.infrastructure.repository.DeclinedMatchRepository;
import com.gamebuddy.match.interfaces.dto.AcceptResponseBody;
import com.gamebuddy.match.interfaces.dto.GamerDto;
import com.gamebuddy.match.interfaces.dto.LikedYouResponseBody;
import com.gamebuddy.match.interfaces.dto.RecommendationResponseBody;
import com.gamebuddy.match.interfaces.dto.SwipeAllowanceResponseBody;
import com.gamebuddy.match.interfaces.request.ColdStartRequest;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.request.PredictRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.LikedYouResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
import com.gamebuddy.match.interfaces.response.SwipeAllowanceResponse;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.AvatarsRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.repository.GamesRepository;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMatchService implements MatchService {

    /** Upper bound on a recommendation page; both sources were previously unbounded. */
    private static final int MAX_RECOMMENDATIONS = 50;

    /**
     * How many candidates to ask the model for.
     *
     * <p>Deliberately larger than {@link #MAX_RECOMMENDATIONS}: age band, blocks and
     * banned accounts are enforced here rather than by the model, so some of what comes
     * back is dropped before the page is assembled. Over-fetching means a gamer whose
     * nearest neighbours happen to be in the other age band still gets a full page.
     */
    private static final int RECOMMENDATION_FETCH_SIZE = MAX_RECOMMENDATIONS * 3;

    /**
     * Fraction of each page given to randomly chosen candidates rather than ranked ones.
     *
     * <p>Ten percent is enough to keep every gamer reachable and to generate unbiased
     * impressions, while costing little relevance. Raising it trades match quality for
     * discovery; lowering it lets the popularity feedback loop tighten again.
     */
    private static final float EXPLORATION_RATE = 0.10f;

    private final PredictClient predictClient;
    private final GamerRepository gamerRepository;
    private final GamesRepository gamesRepository;
    private final AvatarsRepository avatarsRepository;
    private final AvatarUrls avatarUrls;
    private final CosmeticUrls cosmeticUrls;
    private final ChatMapper chatMapper;
    private final ApplicationEventPublisher events;
    private final SwipeQuota swipeQuota;

    /** Throttles scripted swiping; see {@link #requireNotFlooding}. */
    private final RateLimiter decisionRateLimiter;

    private final DeclinedMatchRepository declinedMatches;
    private final Clock clock;

    /**
     * The cutoff for the exclusion queries: declines older than this stop hiding anyone.
     *
     * <p>Declines used to be permanent, which meant the candidate pool could only shrink:
     * the ranking is deterministic, so anyone ever passed over never came back and an active
     * swiper eventually ran out of people entirely.
     */
    private Instant declineHorizon() {
        return clock.instant().minus(DeclinedMatch.RECYCLE_AFTER);
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(Gamer principal) {
        Gamer gamer = reload(principal);

        // Everyone already decided on, sent to the model so it can rank *past* them.
        // Filtering the response instead is what made the feed run dry: the ranking is a
        // deterministic function of profiles, so the same page came back every time and
        // the next retrain returned nearly the same order.
        Set<String> decided = new HashSet<>();
        decided.add(gamer.getUserId());
        gamer.getApprovedMatches().forEach(other -> decided.add(other.getUserId()));
        // Only declines still inside the recycling window. An expired one stops excluding
        // its target, so the pool refills instead of shrinking to nothing.
        decided.addAll(declinedMatches.findActiveExclusions(gamer.getUserId(), declineHorizon()));

        List<String> candidates = predict(gamer, decided);

        // findAllById rather than one findById per candidate, and a candidate the model
        // knows about but the database no longer does is skipped rather than turned into
        // a USER_NOT_FOUND that fails the whole screen.
        List<Gamer> recommended = gamerRepository.findAllById(candidates);
        if (recommended.size() != candidates.size()) {
            log.warn("The model returned {} unknown gamer id(s)", candidates.size() - recommended.size());
        }

        List<Gamer> ranked = pairable(gamer, rankedAsModelOrdered(candidates, recommended));
        List<Gamer> explored = exploration(gamer, ranked, decided);
        List<Gamer> page = merge(ranked, explored);

        recordImpressions(gamer, page, explored);
        return recommendationResponse(page);
    }

    /**
     * Records what was shown, so the model can later be told whether it was any good.
     *
     * <p>Published as an event and written after commit on another thread: analytics must
     * never be able to slow down or fail the feed it is measuring.
     */
    private void recordImpressions(Gamer gamer, List<Gamer> page, List<Gamer> explored) {
        if (page.isEmpty()) {
            return;
        }
        Set<String> exploredIds = explored.stream().map(Gamer::getUserId).collect(Collectors.toSet());

        List<ServedCandidate> served = new ArrayList<>(page.size());
        for (int position = 0; position < page.size(); position++) {
            String candidateId = page.get(position).getUserId();
            served.add(new ServedCandidate(
                    candidateId,
                    position,
                    exploredIds.contains(candidateId) ? ImpressionSource.EXPLORATION : ImpressionSource.MODEL));
        }
        events.publishEvent(new RecommendationServedEvent(gamer.getUserId(), served));
    }

    /**
     * Mixes a few randomly chosen gamers into an otherwise similarity-ranked page.
     *
     * <p>Ranking purely by similarity is self-reinforcing. A gamer whose taste resembles
     * nobody's is never shown, so is never liked, so the desirability prior — which learns
     * from likes per impression — scores them lower still. New accounts start in exactly
     * that position, which is the worst possible moment to be invisible.
     *
     * <p>The explored slots are also the only unbiased impressions the system produces:
     * every other impression is conditioned on the model already believing in the pairing,
     * so fitting the prior on those alone measures the model's own past opinions. This is
     * the cheapest available fix for both problems at once.
     */
    private List<Gamer> exploration(Gamer gamer, List<Gamer> ranked, Set<String> decided) {
        int slots = Math.round(ranked.size() * EXPLORATION_RATE);
        if (slots == 0) {
            return List.of();
        }

        Set<String> alreadyOnPage = new HashSet<>(decided);
        ranked.forEach(candidate -> alreadyOnPage.add(candidate.getUserId()));

        return gamerRepository
                .findRandomPairable(
                        AgeBand.of(gamer.getAge()) == AgeBand.MINOR, alreadyOnPage.toArray(String[]::new), slots)
                .stream()
                // The SQL cannot see the block graph, which lives in a join table on both
                // sides, so the same filter every other path uses is applied here too.
                .filter(gamer::isPairableWith)
                .toList();
    }

    /**
     * Explored candidates replace the tail of the page rather than extending it, so
     * exploration costs a little relevance instead of quietly growing the response.
     */
    private List<Gamer> merge(List<Gamer> ranked, List<Gamer> explored) {
        if (explored.isEmpty()) {
            return ranked;
        }
        List<Gamer> page = new ArrayList<>(ranked.subList(0, Math.max(0, ranked.size() - explored.size())));
        page.addAll(explored);
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getSelectedGameRecommendations(Gamer principal, String gameId) {
        Gamer gamer = reload(principal);
        Games game = gamesRepository
                .findById(gameId)
                // Was DB_ERROR, i.e. an HTTP 500 for a client asking about a game that
                // does not exist.
                .orElseThrow(() -> new BusinessException(TransactionCode.GAME_NOT_FOUND));

        // A copy: the old code called remove() on the managed collection, which told
        // Hibernate to delete those rows from gamer_games_join. Asking for
        // recommendations quietly un-liked the game for the other gamers.
        Set<Gamer> candidates = new LinkedHashSet<>(game.getGamers());
        candidates.remove(gamer);
        candidates.removeAll(gamer.getApprovedMatches());
        Set<String> recentlyDeclined =
                new HashSet<>(declinedMatches.findActiveExclusions(gamer.getUserId(), declineHorizon()));
        candidates.removeIf(candidate -> recentlyDeclined.contains(candidate.getUserId()));

        return recommendationResponse(pairable(gamer, candidates));
    }

    /**
     * Records that this gamer wants to match, and detects whether it is now mutual.
     *
     * <p>Nothing previously detected a mutual match at all. One side's acceptance was
     * written and that was the end of it: neither gamer was told the other had accepted
     * them back, and the only way to discover a match existed was to try to send a
     * message and see whether it was refused. For an app whose entire premise is matching
     * people, the moment the match happens is the product, and it did not exist.
     */
    @Override
    @Transactional
    public AcceptResponse acceptGamer(Gamer principal, GamerRequest request) {
        Gamer gamer = reload(principal);
        Gamer target = requireGamer(request.getUserId());
        requirePairable(gamer, target);
        requireNotFlooding(gamer);
        // Charged before the decision is recorded, so a refused accept changes nothing.
        // An accept costs a swipe *and* draws on the much tighter accept sub-cap.
        swipeQuota.charge(gamer, true);

        gamer.getApprovedMatches().add(target);
        // A decision replaces the previous one rather than sitting alongside it; the two
        // sets could both contain the same gamer, and getRecommendations then removed
        // them twice while acceptGamer and declineGamer disagreed about the outcome.
        declinedMatches.clear(gamer.getUserId(), target.getUserId());

        boolean mutual = target.getApprovedMatches().contains(gamer);
        if (mutual) {
            // Both sides get told, because both sides have just gained the ability to
            // start a conversation.
            notifyMatched(gamer, target);
            notifyMatched(target, gamer);

            // The match badges are not awarded here. This module used to own a copy of
            // "add it if missing, then notify" and the threshold that went with it; both
            // now live in BadgeService, which counts mutual matches the same way this
            // does and grants whatever is finished the next time the gamer looks.
            gamerRepository.save(target);
        }
        gamerRepository.save(gamer);

        AcceptResponse response = new AcceptResponse();
        response.setBody(new BaseBody<>(new AcceptResponseBody(mutual, mutual ? "It's a match!" : "Gamer accepted")));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /** The gamers who have accepted this gamer back. Only these may be chatted with. */
    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getMatches(Gamer principal) {
        Gamer gamer = reload(principal);
        return recommendationResponse(mutualMatches(gamer));
    }

    @Override
    @Transactional(readOnly = true)
    public LikedYouResponse getWhoLikedYou(Gamer principal) {
        Gamer gamer = reload(principal);

        // One-sided likes only. Someone this gamer has already answered is a match or a
        // pass, not a pending like, and showing them again would be a paid feature that
        // sells information the gamer already has.
        Set<String> recentlyDeclined =
                new HashSet<>(declinedMatches.findActiveExclusions(gamer.getUserId(), declineHorizon()));
        List<Gamer> admirers = gamerRepository.findPendingAdmirers(gamer.getUserId()).stream()
                .filter(other -> !gamer.getApprovedMatches().contains(other))
                .filter(other -> !recentlyDeclined.contains(other.getUserId()))
                .filter(gamer::isPairableWith)
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        boolean unlocked = swipeQuota.effectiveTier(gamer).canSeeWhoLikedYou();

        LikedYouResponseBody body = new LikedYouResponseBody();
        // The count is free even when the identities are not: it is the whole hook, and
        // withholding it would leave nothing to upgrade for.
        body.setCount(admirers.size());
        body.setLocked(!unlocked);
        body.setLikedYou(unlocked ? toDtos(admirers) : List.of());

        LikedYouResponse response = new LikedYouResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SwipeAllowanceResponse getSwipeAllowance(Gamer principal) {
        Gamer gamer = reload(principal);
        SwipeQuota.SwipeAllowance allowance = swipeQuota.remaining(gamer);

        SwipeAllowanceResponseBody body = new SwipeAllowanceResponseBody(
                allowance.tier().name(),
                allowance.unlimited() ? 0 : allowance.remainingSwipes(),
                allowance.unlimited() ? 0 : allowance.remainingAccepts(),
                allowance.unlimited(),
                allowance.resetsAt());

        SwipeAllowanceResponse response = new SwipeAllowanceResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional
    public DefaultMessageResponse declineGamer(Gamer principal, GamerRequest request) {
        Gamer gamer = reload(principal);
        Gamer target = requireGamer(request.getUserId());

        if (gamer.getUserId().equals(target.getUserId())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot match with yourself");
        }

        requireNotFlooding(gamer);
        // Costs a swipe from the same budget as an accept, but does not touch the accept
        // sub-cap: a gamer who declines all day never uses up their likes.
        swipeQuota.charge(gamer, false);

        // Declining is otherwise always allowed, whatever the age band or block state — a
        // gamer must be able to dismiss anyone who reached their screen.
        declinedMatches.save(new DeclinedMatch(gamer.getUserId(), target.getUserId(), clock.instant()));
        gamer.getApprovedMatches().remove(target);
        gamerRepository.save(gamer);

        return DefaultMessageResponse.of("Gamer declined");
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private Gamer reload(Gamer principal) {
        return requireGamer(principal.getUserId());
    }

    /**
     * Refuses decisions arriving faster than a person can make them.
     *
     * <p>Separate from the daily allowance, and solving a different problem. The allowance
     * is a monetisation lever measured in a day; this is an abuse control measured in a
     * minute. A script enumerating the population is not stopped by a daily cap — it just
     * runs to the cap instantly, every day — and a real user never approaches this rate, so
     * throttling here costs nothing legitimate.
     */
    private void requireNotFlooding(Gamer gamer) {
        if (!decisionRateLimiter.tryAcquire(gamer.getUserId())) {
            log.warn("Throttled a flood of match decisions from {}", gamer.getUserId());
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }
    }

    /**
     * Refuses a pairing that must never happen.
     *
     * <p>Three rules, none of which existed: a minor is never paired with an adult, a
     * blocked gamer is never paired with the gamer who blocked them (in either
     * direction), and a banned account is never paired with anyone.
     */
    private void requirePairable(Gamer gamer, Gamer target) {
        if (gamer.getUserId().equals(target.getUserId())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot match with yourself");
        }
        if (gamer.hasBlockRelationshipWith(target)) {
            // Deliberately the same code either way: telling the caller "they blocked
            // you" is information they should not have.
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
        if (!AgeBand.compatible(gamer.getAge(), target.getAge())) {
            throw new BusinessException(TransactionCode.AGE_BAND_MISMATCH);
        }
        if (!target.isAccountNonLocked()) {
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
    }

    /** Drops candidates this gamer must not be shown, and caps the page. */
    private List<Gamer> pairable(Gamer gamer, Collection<Gamer> candidates) {
        return candidates.stream()
                .filter(candidate -> !candidate.getUserId().equals(gamer.getUserId()))
                .filter(gamer::isPairableWith)
                // A popular game is liked by most of the user base, and the model's
                // result was unbounded too, so both endpoints could return every gamer
                // in the system in one response.
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    /** Matches are mutual by definition; a one-sided acceptance is not one. */
    private List<Gamer> mutualMatches(Gamer gamer) {
        return gamer.getApprovedMatches().stream()
                .filter(other -> other.getApprovedMatches().contains(gamer))
                .filter(other -> !gamer.hasBlockRelationshipWith(other))
                .toList();
    }

    private void notifyMatched(Gamer recipient, Gamer other) {
        events.publishEvent(new NotificationRequestedEvent(
                recipient.getFcmToken(),
                Constants.MATCH_TITLE,
                String.format(Constants.MATCH_BODY, other.getGamerUsername()),
                NotificationKind.MATCH,
                other.getUserId()));
    }

    private Gamer requireGamer(String userId) {
        return gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    /**
     * Asks the model to rank candidates, falling back to the cold-start path.
     *
     * <p>Catches RuntimeException rather than FeignException, which no longer exists here.
     *
     * <p>The artefact is trained offline and only knows gamers who existed at the last
     * run; {@code /predict} answers an unknown id with an empty list rather than an
     * error. That silence is the trap — without this fallback, every account created
     * since the last retrain gets an empty deck, and at launch that is every account.
     *
     * <p>An empty result is therefore treated as "the model has not met this gamer",
     * not as "there is nobody". A gamer who genuinely has no candidates left produces an
     * empty cold-start result too, so nothing is lost by trying.
     */
    private List<String> predict(Gamer gamer, Set<String> exclude) {
        String userId = gamer.getUserId();
        try {
            List<String> ranked = predictClient
                    .predict(new PredictRequest(userId, exclude, RECOMMENDATION_FETCH_SIZE))
                    .similarUsers();
            if (!ranked.isEmpty()) {
                return ranked;
            }

            // Names, not ids: the model was trained on the catalogue's names and has
            // never seen our UUIDs.
            List<String> games =
                    gamer.getLikedgames().stream().map(Games::getGameName).toList();
            List<String> keywords =
                    gamer.getKeywords().stream().map(Keywords::getKeywordName).toList();
            if (games.isEmpty() && keywords.isEmpty()) {
                return List.of();
            }

            log.debug("Model has no vector for {}; ranking from the profile instead", userId);
            return predictClient
                    .predictColdStart(new ColdStartRequest(userId, games, keywords, exclude, RECOMMENDATION_FETCH_SIZE))
                    .similarUsers();
        } catch (RuntimeException e) {
            log.warn("Recommendation model unavailable for {}", userId, e);
            throw new BusinessException(TransactionCode.RECOMMENDER_SERVICE_ERROR, e);
        }
    }

    /**
     * Restores the model's ordering after {@code findAllById}.
     *
     * <p>{@code findAllById} issues a {@code WHERE id IN (...)} and returns rows in
     * whatever order the database chose, so the ranking the model computed was being
     * discarded — and because {@link #pairable} then keeps only the first
     * {@value #MAX_RECOMMENDATIONS}, the page was an arbitrary subset rather than the most
     * similar gamers. Every recommendation was effectively unranked.
     */
    private List<Gamer> rankedAsModelOrdered(List<String> ranking, List<Gamer> fetched) {
        Map<String, Gamer> byId = fetched.stream().collect(Collectors.toMap(Gamer::getUserId, g -> g));
        return ranking.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Maps gamers to DTOs, resolving every avatar in one query rather than per row. */
    private List<GamerDto> toDtos(List<Gamer> gamers) {
        Map<String, String> avatars = avatarUrls.visibleTo(gamers);

        return gamers.stream()
                .map(g -> {
                    GamerDto dto = chatMapper.toDto(g);
                    dto.setAvatar(avatars.get(g.getUserId()));
                    // No map needed: Cosmetic is @BatchSize(50), so touching the lazy
                    // reference across a page of gamers costs one extra query, not one
                    // per row.
                    dto.setFrame(cosmeticUrls.frameUrl(g));
                    dto.setFavoriteGames(chatMapper.toGameDtos(g.getLikedgames()));
                    dto.setSelectedKeywords(g.getKeywords().stream()
                            .map(Keywords::getKeywordName)
                            .toList());
                    return dto;
                })
                .toList();
    }

    private RecommendationResponse recommendationResponse(List<Gamer> gamers) {
        RecommendationResponse response = new RecommendationResponse();
        RecommendationResponseBody body = new RecommendationResponseBody();
        body.setRecommendedGamers(toDtos(gamers));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
