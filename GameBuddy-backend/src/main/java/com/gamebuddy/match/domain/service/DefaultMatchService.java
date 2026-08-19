package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.AgeBand;
import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.common.enums.SubscriptionTier;
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
import com.gamebuddy.match.infrastructure.entity.UnlockedAdmirer;
import com.gamebuddy.match.infrastructure.repository.DeclinedMatchRepository;
import com.gamebuddy.match.infrastructure.entity.SuperLike;
import com.gamebuddy.match.infrastructure.repository.SuperLikeRepository;
import com.gamebuddy.match.infrastructure.repository.UnlockedAdmirerRepository;
import com.gamebuddy.match.interfaces.dto.AcceptResponseBody;
import com.gamebuddy.match.interfaces.dto.ConsumableResponseBody;
import com.gamebuddy.match.interfaces.dto.GamerDto;
import com.gamebuddy.match.interfaces.dto.LikedYouResponseBody;
import com.gamebuddy.match.interfaces.dto.RecommendationResponseBody;
import com.gamebuddy.match.interfaces.dto.RewindResponseBody;
import com.gamebuddy.match.interfaces.dto.SwipeAllowanceResponseBody;
import com.gamebuddy.match.interfaces.request.ColdStartRequest;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.request.PredictRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.ConsumableResponse;
import com.gamebuddy.match.interfaces.response.LikedYouResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
import com.gamebuddy.match.interfaces.response.RewindResponse;
import com.gamebuddy.match.interfaces.response.SwipeAllowanceResponse;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
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
import org.springframework.web.client.HttpClientErrorException;

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
    private final UnlockedAdmirerRepository unlockedAdmirers;
    private final SuperLikeRepository superLikes;
    private final CoinLedger coins;
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
        return getRecommendations(principal, FeedFilters.none());
    }

    @Override
    @Transactional
    public RecommendationResponse getRecommendations(Gamer principal, FeedFilters filters) {
        Gamer gamer = reload(principal);

        // The entitlement is checked before any work is done, and only when the request
        // actually narrows anything — an unfiltered feed is free, and asking for one must
        // never cost a 402. Same accessor as every other tier check, so a lapsed
        // subscription stops working here at the same instant it stops working elsewhere.
        if (filters.narrowing() && !swipeQuota.effectiveTier(gamer).canUseAdvancedFilters()) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }

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

        // Who the filters allow, sent to the model as the pool it may rank within — for
        // exactly the reason above: the model returns its top N by similarity and a filter
        // applied to the answer can only remove from those N. It can never reach the person
        // ranked 300th who is the only one online in your country, so the filter that sounds
        // most valuable is the one that most reliably returned nothing.
        List<String> eligible = eligibleFor(filters);

        List<String> candidates = predict(gamer, decided, eligible);

        // findAllById rather than one findById per candidate, and a candidate the model
        // knows about but the database no longer does is skipped rather than turned into
        // a USER_NOT_FOUND that fails the whole screen.
        List<Gamer> recommended = gamerRepository.findAllById(candidates);
        if (recommended.size() != candidates.size()) {
            log.warn("The model returned {} unknown gamer id(s)", candidates.size() - recommended.size());
        }

        List<Gamer> ranked = filtered(pairable(gamer, rankedAsModelOrdered(candidates, recommended)), filters);
        // Exploration is filtered too, in its own query and again here. It exists to surface
        // people the model would never rank, and a filtered feed that quietly injects
        // somebody playing a different game is not showing an overlooked candidate — it is
        // ignoring the request.
        List<Gamer> explored = filtered(exploration(gamer, ranked, decided, filters), filters);
        // No promoted slots any more: the deck boost was retired in favour of a lobby boost,
        // where the thing being promoted is a plan somebody can join rather than a face.
        List<Gamer> page = merge(ranked, explored);

        recordImpressions(gamer, page, explored);
        return recommendationResponse(page);
    }

    /**
     * The largest eligible set worth sending to the model.
     *
     * <p>Must not exceed {@code MAX_INCLUSIONS} in the model service, which rejects a longer
     * list outright — the whole failure being fixed here was a list the model had already
     * declared too large, reported to the user as the recommender being down.
     *
     * <p>Crossing it is not an error and must never become one. A set this large means the
     * filter has excluded hardly anybody, so ranking unfiltered and applying
     * {@link #filtered} to the answer gives very nearly the same deck — the top candidates
     * are overwhelmingly eligible when almost everyone is. That is the whole reason this
     * direction is the right one: the fallback is only ever needed where it costs nothing.
     */
    private static final int MAX_ELIGIBLE = 50_000;

    /**
     * The gamers a narrowed feed may draw from, or null when nothing was asked for.
     *
     * <p>Null and empty are different answers and the difference is load-bearing. Null is
     * "not filtering" and the model ranks over everybody. Empty is "the filter matched
     * nobody" and must produce an empty deck — answering it with an unfiltered one would
     * show a gamer who asked for people online in their country a page of people who are
     * neither, which looks like the filter being ignored.
     */
    private List<String> eligibleFor(FeedFilters filters) {
        if (!filters.narrowing()) {
            return null;
        }

        List<String> eligible = gamerRepository.findIdsMatchingFilters(
                filters.gameId(),
                filters.country(),
                filters.activeSince(clock),
                filters.platform() == null ? null : filters.platform().name());

        if (eligible.size() > MAX_ELIGIBLE) {
            log.warn(
                    "Filter {} matches {} gamers, past the {} the model accepts; ranking"
                            + " unfiltered and narrowing the answer instead",
                    filters,
                    eligible.size(),
                    MAX_ELIGIBLE);
            return null;
        }
        return eligible;
    }

    /**
     * Applies the filters, if any were asked for.
     *
     * <p>Kept even though the excluded ids were already withheld from the model. The two
     * are not redundant: the exclusion decides <em>who gets ranked</em>, this decides
     * <em>who gets shown</em>, and between them sits a model call that can return an id
     * the exclusion list should have covered — a stale artefact, a cold-start path, or a
     * gamer who went idle in the seconds since the query ran. Showing an offline person
     * under an "online now" filter is the one outcome that makes the feature look broken,
     * so the cheap in-memory check stays.
     */
    private List<Gamer> filtered(List<Gamer> candidates, FeedFilters filters) {
        if (!filters.narrowing()) {
            return candidates;
        }
        return candidates.stream().filter(c -> filters.matches(c, clock)).toList();
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
     *
     * <p><b>Whatever the ranking cannot fill, this does — and that floor is the whole
     * difference between a working deck and an empty one on a young install.</b> The slot
     * count used to be a flat tenth of the ranking, which meant the one mechanism that can
     * reach a gamer the model has never heard of returned nothing in precisely the case
     * where the model has never heard of anybody: zero ranked candidates times ten percent
     * is zero. That is not a hypothetical. {@code /predict} can only return ids that are
     * <em>inside the trained artefact</em>, so a freshly deployed environment — where the
     * artefact is the synthetic one baked into the image and every id in it belongs to a
     * gamer this database has never had — resolves every recommendation to nothing in
     * {@code findAllById} above. Two real people signed up minutes apart both saw "that's
     * everyone for now", with no error anywhere, because the deck's only other source of
     * candidates had sized itself off an empty list.
     *
     * <p>Filling to {@link #MAX_RECOMMENDATIONS} is the right shape rather than a patch for
     * that one situation. A short ranking means the same thing every time it happens — the
     * model could not name a full page of people — and the database can still name them:
     * they are ordinary candidates who pass every gate, just unranked. That covers a heavy
     * swiper who has reached the end of what the model will rank for them, an artefact that
     * has gone stale between retrains, and a population too small to have been trained on at
     * all, which is every product on its first day. The page stays bounded either way, and
     * at healthy scale the ranking fills it and this stays the tenth it always was.
     */
    private List<Gamer> exploration(Gamer gamer, List<Gamer> ranked, Set<String> decided, FeedFilters filters) {
        int slots = Math.max(Math.round(ranked.size() * EXPLORATION_RATE), MAX_RECOMMENDATIONS - ranked.size());
        if (slots <= 0) {
            return List.of();
        }

        Set<String> alreadyOnPage = new HashSet<>(decided);
        ranked.forEach(candidate -> alreadyOnPage.add(candidate.getUserId()));

        // The filters go into the query rather than onto its result, and this is a trap worth
        // naming: exploration used to be filter-correct by accident, because `decided`
        // carried every gamer the filter ruled out and the query could not return one.
        // Sending the eligible set to the model instead ended that. Drawing at random from
        // the whole population and discarding the misses afterwards would leave the
        // exploration slots empty under exactly the narrow filters that make them valuable.
        return gamerRepository
                .findRandomPairable(
                        AgeBand.of(gamer.getAge()) == AgeBand.MINOR,
                        alreadyOnPage.toArray(String[]::new),
                        filters.gameId(),
                        filters.country(),
                        filters.activeSince(clock),
                        filters.platform() == null ? null : filters.platform().name(),
                        slots)
                .stream()
                // The SQL cannot see the block graph, which lives in a join table on both
                // sides, so the same filter every other path uses is applied here too.
                .filter(gamer::isPairableWith)
                .toList();
    }

    /**
     * Explored candidates displace the tail of a full page rather than extending it, so
     * exploration costs a little relevance instead of quietly growing the response — but
     * they never displace a ranked candidate the page still has room for.
     *
     * <p>Both halves matter, and the second only started to once {@link #exploration} was
     * allowed to fill an under-full page. Dropping the tail unconditionally would take a
     * ranking of five and thirty-five explored candidates and throw all five away: the page
     * had space for every one of them, and they are the only candidates on it the model
     * actually vouched for. So the cut is whichever of the two rules keeps more of the
     * ranking, and on a full page they agree.
     *
     * <p>The result is bounded by {@link #MAX_RECOMMENDATIONS} however the two lists divide
     * it, which is what the original rule was protecting.
     */
    private List<Gamer> merge(List<Gamer> ranked, List<Gamer> explored) {
        if (explored.isEmpty()) {
            return ranked;
        }
        int keep = Math.min(
                ranked.size(), Math.max(ranked.size() - explored.size(), MAX_RECOMMENDATIONS - explored.size()));
        List<Gamer> page = new ArrayList<>(ranked.subList(0, Math.max(0, keep)));
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
        // Refused rather than downgraded when none are owned. Somebody who asked to make a
        // statement and silently made an ordinary like would never know, and would keep
        // believing they had spent one.
        boolean superLike = request.isSuperLike();
        if (superLike && gamer.getSuperLikes() <= 0) {
            throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH, "you have no super likes");
        }

        // Charged before the decision is recorded, so a refused accept changes nothing.
        // An accept costs a swipe *and* draws on the much tighter accept sub-cap.
        swipeQuota.charge(gamer, true);

        if (superLike) {
            gamer.setSuperLikes(gamer.getSuperLikes() - 1);
        }

        gamer.getApprovedMatches().add(target);
        // A decision replaces the previous one rather than sitting alongside it; the two
        // sets could both contain the same gamer, and getRecommendations then removed
        // them twice while acceptGamer and declineGamer disagreed about the outcome.
        declinedMatches.clear(gamer.getUserId(), target.getUserId());
        rememberDecision(gamer, target, true);

        // Kept, because nothing else does. The balance has already been spent and the push
        // has already been queued, and both are moments — after them a super like used to be
        // indistinguishable from any other like, including on the one screen that exists to
        // show who likes you. `save` rather than an insert guard: the primary key is the
        // pair, so re-liking somebody after a rewind overwrites rather than collides.
        if (superLike) {
            superLikes.save(new SuperLike(gamer.getUserId(), target.getUserId(), clock.instant()));
        }

        boolean mutual = target.getApprovedMatches().contains(gamer);

        // Only when it is not already a match. Two notifications a second apart, the first
        // saying somebody likes you and the second that you matched, is noise — and the
        // match is the better news, so it wins.
        if (superLike && !mutual) {
            events.publishEvent(new NotificationRequestedEvent(
                    target.getUserId(),
                    target.getFcmToken(),
                    Constants.SUPER_LIKE_TITLE,
                    Constants.SUPER_LIKE_BODY,
                    NotificationKind.SUPER_LIKE,
                    gamer.getUserId()));
        }

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

        if (unlocked) {
            body.setLikedYou(markSuperLikes(gamer, toDtos(admirers)));
        } else {
            // Without Gold, only the ones already paid for by name. The list still reports
            // the full count above, so the screen shows "three more" rather than pretending
            // the bought one is all there is.
            Set<String> bought = new HashSet<>(unlockedAdmirers.findAdmirerIds(gamer.getUserId()));
            body.setLikedYou(markSuperLikes(
                    gamer,
                    toDtos(admirers.stream()
                            .filter(other -> bought.contains(other.getUserId()))
                            .toList())));
        }

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
                allowance.resetsAt(),
                gamer.getSuperLikes());

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
        rememberDecision(gamer, target, false);
        gamerRepository.save(gamer);

        return DefaultMessageResponse.of("Gamer declined");
    }

    /**
     * Notes what was just swiped, so it can be taken back.
     *
     * <p>Overwrites rather than appends: only the most recent decision is rewindable, and
     * keeping one slot is what makes that true without a second counter to get wrong.
     */
    private void rememberDecision(Gamer gamer, Gamer target, boolean accept) {
        gamer.setLastDecisionUserId(target.getUserId());
        gamer.setLastDecisionAccept(accept);
        gamer.setLastDecisionAt(clock.instant());
    }

    /**
     * Takes back the last swipe.
     *
     * <p>Refuses a like that was answered. Undoing it would delete a conversation both
     * sides can already see and take a match away from somebody who did nothing but say
     * yes — a rewind is allowed to undo <em>your</em> decision, not somebody else's.
     *
     * <p>The quota is refunded, because the swipe is being un-made. Not refunding it would
     * mean a rewind costs coins <em>and</em> a like, which is the opposite of removing a
     * regret.
     */
    @Override
    @Transactional
    public RewindResponse rewind(Gamer principal) {
        Gamer gamer = reload(principal);

        String targetId = gamer.getLastDecisionUserId();
        if (targetId == null || gamer.getLastDecisionAccept() == null) {
            throw new BusinessException(TransactionCode.NOTHING_TO_REWIND);
        }

        Gamer target = requireGamer(targetId);
        boolean wasAccept = Boolean.TRUE.equals(gamer.getLastDecisionAccept());

        if (wasAccept && target.getApprovedMatches().contains(gamer)) {
            throw new BusinessException(TransactionCode.REWIND_MATCHED);
        }

        int cost = BoostPolicy.rewindCost(swipeQuota.effectiveTier(gamer));
        if (cost > 0) {
            if (gamer.getCoin() < cost) {
                throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
            }
            coins.spend(gamer, cost, CoinReason.REWIND);
        }

        if (wasAccept) {
            gamer.getApprovedMatches().remove(target);
            // Whether or not this one was a super like — `clear` is a no-op when it was
            // not, and asking first would cost a query to save nothing.
            superLikes.clear(gamer.getUserId(), targetId);
        } else {
            declinedMatches.clear(gamer.getUserId(), targetId);
        }

        swipeQuota.refund(gamer, wasAccept);

        // Cleared so the same regret cannot be undone twice.
        gamer.setLastDecisionUserId(null);
        gamer.setLastDecisionAccept(null);
        gamer.setLastDecisionAt(null);
        gamerRepository.save(gamer);

        RewindResponse response = new RewindResponse();
        response.setBody(new BaseBody<>(new RewindResponseBody(candidate(target), cost, gamer.getCoin())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * Buys a consumable with coins.
     *
     * <p>Read-check-write in one transaction, so it runs under the {@code @Version} lock on
     * {@link Gamer} — two taps of a buy button would otherwise both read the same balance,
     * both find it sufficient, and hand over two items for the price of one.
     *
     * <p>{@link Consumable#UNLOCK_ADMIRER} is not sold here: it needs to know <em>whom</em>
     * it is unlocking, and a purchase that grants "one unlock" to be spent later is exactly
     * the counter this deliberately avoids. See {@link #unlockAdmirer}.
     */
    @Override
    @Transactional
    public ConsumableResponse buyConsumable(Gamer principal, Consumable item) {
        if (item == Consumable.UNLOCK_ADMIRER) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "unlock is bought against a gamer");
        }

        Gamer gamer = reload(principal);
        spend(gamer, item.cost(), item == Consumable.SUPER_LIKE ? CoinReason.SUPER_LIKE : CoinReason.EXTRA_LIKES);

        switch (item) {
            case SUPER_LIKE -> gamer.setSuperLikes(gamer.getSuperLikes() + 1);
            case EXTRA_LIKES -> {
                // Anchored to the current window so a purchase made before the first swipe
                // of the day is not wiped by the lazy reset that swipe would trigger.
                Instant now = clock.instant();
                if (gamer.getQuotaResetAt() == null || !gamer.getQuotaResetAt().isAfter(now)) {
                    gamer.setSwipesUsed(0);
                    gamer.setAcceptsUsed(0);
                    gamer.setBonusAccepts(0);
                    gamer.setQuotaResetAt(now.plus(SwipeQuota.WINDOW));
                }
                gamer.setBonusAccepts(gamer.getBonusAccepts() + Consumable.EXTRA_LIKES_COUNT);
            }
            default -> throw new BusinessException(TransactionCode.INVALID_REQUEST, "not for sale");
        }

        gamerRepository.save(gamer);
        log.info("{} bought {} for {} coins", gamer.getUserId(), item, item.cost());
        return consumableResponse(gamer);
    }

    /**
     * Pays to see one particular admirer.
     *
     * <p>Bought against a person rather than as a token, so what the gamer receives is the
     * face they were looking at when they decided to pay. Already-unlocked and already-Gold
     * both refuse rather than charging again — the second is the one that would really
     * sting, because it takes coins for something the subscription already gives.
     */
    @Override
    @Transactional
    public LikedYouResponse unlockAdmirer(Gamer principal, String admirerId) {
        Gamer gamer = reload(principal);

        if (swipeQuota.effectiveTier(gamer).canSeeWhoLikedYou()) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "your membership already shows this");
        }
        if (unlockedAdmirers.existsByUserIdAndAdmirerId(gamer.getUserId(), admirerId)) {
            throw new BusinessException(TransactionCode.COSMETIC_ALREADY_OWNED);
        }

        // Only somebody actually waiting on an answer can be unlocked. Without this, the
        // endpoint sells the identity of any account whose id is known.
        boolean admires = gamerRepository.findPendingAdmirers(gamer.getUserId()).stream()
                .anyMatch(other -> other.getUserId().equals(admirerId) && gamer.isPairableWith(other));
        if (!admires) {
            throw new BusinessException(TransactionCode.USER_NOT_FOUND);
        }

        spend(gamer, Consumable.UNLOCK_ADMIRER.cost(), CoinReason.UNLOCK_ADMIRER);
        gamerRepository.save(gamer);
        unlockedAdmirers.save(new UnlockedAdmirer(gamer.getUserId(), admirerId, clock.instant()));

        log.info("{} unlocked admirer {}", gamer.getUserId(), admirerId);
        return getWhoLikedYou(gamer);
    }

    /**
     * Reveals the newest admirer still hidden.
     *
     * <p>The server chooses, because the client cannot: a locked admirer is sent with no
     * id at all, and sending ids so the client could pick would hand over the paid feature
     * for free — an id is enough to fetch a public profile.
     *
     * <p>Newest first, so the coins buy the person most likely to still be looking.
     */
    @Override
    @Transactional
    public LikedYouResponse unlockNextAdmirer(Gamer principal) {
        Gamer gamer = reload(principal);

        Set<String> already = new HashSet<>(unlockedAdmirers.findAdmirerIds(gamer.getUserId()));
        Set<String> recentlyDeclined =
                new HashSet<>(declinedMatches.findActiveExclusions(gamer.getUserId(), declineHorizon()));

        String next = gamerRepository.findPendingAdmirers(gamer.getUserId()).stream()
                .filter(other -> !gamer.getApprovedMatches().contains(other))
                .filter(other -> !recentlyDeclined.contains(other.getUserId()))
                .filter(gamer::isPairableWith)
                .map(Gamer::getUserId)
                .filter(id -> !already.contains(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(TransactionCode.NO_ADMIRERS_LEFT));

        return unlockAdmirer(gamer, next);
    }

    /** Takes coins, or refuses. */
    private void spend(Gamer gamer, int cost, CoinReason reason) {
        if (gamer.getCoin() < cost) {
            throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
        }
        coins.spend(gamer, cost, reason);
    }

    private ConsumableResponse consumableResponse(Gamer gamer) {
        ConsumableResponse response = new ConsumableResponse();
        response.setBody(new BaseBody<>(
                new ConsumableResponseBody(gamer.getCoin(), gamer.getSuperLikes(), gamer.getBonusAccepts())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * The authenticated gamer, refused if they are not a participant.
     *
     * <p>Every entry point in this service goes through here, which is why the moderator
     * check sits here rather than at each of them. Keeping the moderator out of other
     * people's decks is only half the rule — they must not be able to swipe either, and
     * the account has no age, so {@code AgeBand} would place it in the minor band and
     * quietly build it a deck of children. The client never shows these screens to a
     * moderator; this is what makes that true rather than merely usual.
     */
    private Gamer reload(Gamer principal) {
        Gamer gamer = requireGamer(principal.getUserId());
        if (!gamer.isDiscoverable()) {
            throw new BusinessException(TransactionCode.FORBIDDEN, "the moderator account cannot match");
        }
        return gamer;
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
        if (!target.isDiscoverable()) {
            // Same code as a blocked account, and for the same reason: an accept aimed at
            // an id the deck never served should not tell the caller what that id is.
            throw new BusinessException(TransactionCode.USER_BLOCKED);
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
                recipient.getUserId(),
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
     *
     * <p>The same fallback is taken <em>first</em> for a gamer whose profile has changed
     * since the artefact was trained. Their vector exists, so {@code /predict} answers
     * confidently — with the games and keywords they have since replaced. An out-of-date
     * answer is worse than the cold-start one, which is computed from what they like now.
     *
     * <p><b>An empty eligible set short-circuits both calls.</b> That interaction is worth
     * spelling out: an empty result normally means "the model has not met this gamer" and
     * triggers the cold-start fallback, but under a filter that matched nobody it means
     * "nobody qualifies" — a different answer with the same shape. Without this the service
     * would make two round trips to be told the same thing twice.
     *
     * <p><b>A model that cannot answer returns nothing rather than failing the screen.</b>
     * This used to throw {@code RECOMMENDER_SERVICE_ERROR}, which is a 503 — so a model
     * container that had OOMed, or was still loading its artefact after a deploy, took the
     * home screen of the app down with it. Every other tab kept working, which made it look
     * like a bug in the deck rather than one service being unavailable.
     *
     * <p>Degrading is only the better answer because of what the caller does with an empty
     * ranking: {@link #exploration} fills the whole page from the database instead. So the
     * outcome is an unranked deck of real, pairable people rather than an error — worse than
     * a ranked one and enormously better than a blocked screen. Recommendations are an
     * enhancement to a list the database can produce on its own; treating them as a hard
     * dependency was the mistake.
     *
     * <p>Both failures are still logged, and still at the levels that say whose fault it is
     * — that distinction is for whoever reads the logs and was never something to spend the
     * user's home screen on. What is deliberately <em>not</em> done is any kind of circuit
     * breaker: the call already has a client timeout, and a model that is down stops being
     * asked only in the sense that every request pays that timeout once. Worth revisiting if
     * it ever shows up in the latency figures.
     */
    private List<String> predict(Gamer gamer, Set<String> exclude, List<String> include) {
        String userId = gamer.getUserId();
        if (include != null && include.isEmpty()) {
            return List.of();
        }
        try {
            if (gamer.getRecommenderProfileChangedAt() != null) {
                List<String> fresh = coldStart(gamer, exclude, include);
                if (!fresh.isEmpty()) {
                    return fresh;
                }
                // Falls through on purpose. Empty here means the profile has nothing left
                // to rank from, and a stale ranking still beats an empty deck.
                log.debug("Stale profile for {} has nothing to rank from; using the trained vector", userId);
            }

            List<String> ranked = predictClient
                    .predict(new PredictRequest(userId, exclude, include, RECOMMENDATION_FETCH_SIZE))
                    .similarUsers();
            if (!ranked.isEmpty()) {
                return ranked;
            }

            log.debug("Model has no vector for {}; ranking from the profile instead", userId);
            return coldStart(gamer, exclude, include);
        } catch (HttpClientErrorException e) {
            // Kept apart from the case below, and not because the user sees anything
            // different. A 4xx means *we* built a request the model had already declared
            // invalid — the last one was an exclusion list past the model's cap, logged for
            // a day as "recommendation model unavailable" while the model was perfectly
            // healthy. Anything that hides which side is at fault costs exactly that.
            log.error(
                    "The model refused our request for {}: {} {}. Serving an unranked deck.",
                    userId,
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e);
            return List.of();
        } catch (RuntimeException e) {
            log.warn("Recommendation model unavailable for {}; serving an unranked deck", userId, e);
            return List.of();
        }
    }

    /**
     * Ranks from the profile as it stands right now, rather than from the trained vector.
     *
     * <p>Serves two different callers — a gamer the artefact has never seen, and one whose
     * profile has changed since it was built — because the answer to both is the same: work
     * out the query vector live. Nothing else is given up by doing so; the model still
     * clusters and scores the candidates exactly as {@code /predict} would, including the
     * desirability prior.
     *
     * @return the ranking, or empty when there is nothing to rank from
     */
    private List<String> coldStart(Gamer gamer, Set<String> exclude, List<String> include) {
        // Names, not ids: the model was trained on the catalogue's names and has never
        // seen our UUIDs.
        List<String> games =
                gamer.getLikedgames().stream().map(Games::getGameName).toList();
        List<String> keywords =
                gamer.getKeywords().stream().map(Keywords::getKeywordName).toList();
        // Enum names, matching PLATFORM_IDS in the model's catalogue.
        List<String> platforms = gamer.getPlatforms().stream().map(Enum::name).toList();
        if (games.isEmpty() && keywords.isEmpty()) {
            return List.of();
        }
        return predictClient
                .predictColdStart(new ColdStartRequest(
                        gamer.getUserId(), games, keywords, platforms, exclude, include, RECOMMENDATION_FETCH_SIZE))
                .similarUsers();
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

    /** One gamer in the same shape the deck already renders. */
    private GamerDto candidate(Gamer gamer) {
        return toDtos(List.of(gamer)).getFirst();
    }

    /** Maps gamers to DTOs, resolving every avatar in one query rather than per row. */
    /**
     * Flags the admirers whose like was a super like.
     *
     * <p>One query for the whole page, scoped to the ids already on it — the alternative,
     * asking per row, turns a list of thirty into thirty round trips for a boolean.
     *
     * <p>Applied after the list has been narrowed rather than before, so a gamer without
     * Gold does not have the flag computed for admirers they are not allowed to see.
     */
    private List<GamerDto> markSuperLikes(Gamer gamer, List<GamerDto> dtos) {
        if (dtos.isEmpty()) {
            return dtos;
        }
        Set<String> senders = new HashSet<>(superLikes.findSendersAmong(
                gamer.getUserId(), dtos.stream().map(GamerDto::getUserId).toList()));
        dtos.forEach(dto -> dto.setSuperLike(senders.contains(dto.getUserId())));
        return dtos;
    }

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
                    // Labels, not enum names — see GamerDto#platforms. @BatchSize(50) on
                    // the collection keeps a page of cards to one extra query.
                    dto.setPlatforms(
                            g.getPlatforms().stream().map(Platform::label).toList());
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
