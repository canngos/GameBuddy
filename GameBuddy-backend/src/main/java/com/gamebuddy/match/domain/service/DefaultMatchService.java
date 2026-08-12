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
import com.gamebuddy.match.infrastructure.repository.UnlockedAdmirerRepository;
import com.gamebuddy.match.interfaces.dto.AcceptResponseBody;
import com.gamebuddy.match.interfaces.dto.BoostResponseBody;
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
import com.gamebuddy.match.interfaces.response.BoostResponse;
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

        // Anybody the filters rule out goes into the same exclusion set, for exactly the
        // reason above: the model returns its top N by similarity and a filter applied to
        // the answer can only remove from those N. It can never reach the person ranked
        // 300th who is the only one online in your country — so the filter that sounds
        // most valuable is the one that most reliably returned nothing.
        if (filters.narrowing()) {
            decided.addAll(gamerRepository.findIdsExcludedByFilters(
                    filters.gameId(),
                    filters.country(),
                    filters.activeSince(clock),
                    filters.platform() == null ? null : filters.platform().name()));
        }

        List<String> candidates = predict(gamer, decided);

        // findAllById rather than one findById per candidate, and a candidate the model
        // knows about but the database no longer does is skipped rather than turned into
        // a USER_NOT_FOUND that fails the whole screen.
        List<Gamer> recommended = gamerRepository.findAllById(candidates);
        if (recommended.size() != candidates.size()) {
            log.warn("The model returned {} unknown gamer id(s)", candidates.size() - recommended.size());
        }

        List<Gamer> ranked = filtered(pairable(gamer, rankedAsModelOrdered(candidates, recommended)), filters);
        // Exploration is filtered too. It exists to surface people the model would never
        // rank, and a filtered feed that quietly injects somebody playing a different game
        // is not showing an overlooked candidate — it is ignoring the request.
        List<Gamer> explored = filtered(exploration(gamer, ranked, decided), filters);
        List<Gamer> page = boostedFirst(gamer, merge(ranked, explored), decided, filters);

        recordImpressions(gamer, page, explored);
        return recommendationResponse(page);
    }

    /**
     * How many boosted gamers may take the front of one page.
     *
     * <p>Small on purpose. A boost is worth buying because it is seen; it stops being worth
     * buying the moment the top of everyone's deck is nothing but other people's boosts,
     * because then the deck is an advertisement rather than a recommendation and people
     * stop swiping it at all. Three is enough to be noticed and few enough that the ranked
     * feed is still the feed.
     */
    private static final int BOOST_SLOTS = 3;

    /**
     * Puts boosted gamers in this country at the front.
     *
     * <p>Applied after ranking rather than by weighting the model, because a boost is a
     * commercial promise — thirty minutes at the front — and a score nudge is not a promise
     * anybody can check. Doing it here also means the model never learns that paying makes
     * somebody more similar to everyone, which is what a boosted training signal would
     * eventually teach it.
     *
     * <p>Boosted candidates still pass every ordinary gate: age band, blocks, the decided
     * set, and any filters that were asked for. Paying moves you up a queue; it does not
     * get you past the door.
     */
    private List<Gamer> boostedFirst(Gamer gamer, List<Gamer> page, Set<String> decided, FeedFilters filters) {
        if (gamer.getCountry() == null) {
            return page;
        }

        // Everyone already on the page is excluded so a boosted candidate is promoted
        // rather than duplicated.
        Set<String> exclude = new HashSet<>(decided);
        page.forEach(candidate -> exclude.add(candidate.getUserId()));

        List<Gamer> boosted = gamerRepository
                .findBoosted(
                        gamer.getCountry(),
                        AgeBand.of(gamer.getAge()) == AgeBand.MINOR,
                        exclude.toArray(String[]::new),
                        clock.instant(),
                        BOOST_SLOTS)
                .stream()
                // The SQL cannot see the block graph, which lives in a join table on both
                // sides, so the same check every other path uses is applied here too.
                .filter(gamer::isPairableWith)
                .filter(candidate -> filters.matches(candidate, clock))
                .toList();

        if (boosted.isEmpty()) {
            return page;
        }

        List<Gamer> promoted = new ArrayList<>(boosted);
        promoted.addAll(page);
        return promoted;
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
            body.setLikedYou(toDtos(admirers));
        } else {
            // Without Gold, only the ones already paid for by name. The list still reports
            // the full count above, so the screen shows "three more" rather than pretending
            // the bought one is all there is.
            Set<String> bought = new HashSet<>(unlockedAdmirers.findAdmirerIds(gamer.getUserId()));
            body.setLikedYou(toDtos(admirers.stream()
                    .filter(other -> bought.contains(other.getUserId()))
                    .toList()));
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
     * Puts this gamer at the front of decks in their country for half an hour.
     *
     * <p>Free once a week on Gold, otherwise coins. Refuses while one is already running
     * rather than extending it: stacking would let somebody spend four boosts on two hours
     * nobody is awake for, and "you are already boosted" is the answer they actually want.
     */
    @Override
    @Transactional
    public BoostResponse boost(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();

        if (BoostPolicy.boosted(gamer.getBoostExpiresAt(), now)) {
            throw new BusinessException(TransactionCode.BOOST_ALREADY_ACTIVE);
        }

        SubscriptionTier tier = swipeQuota.effectiveTier(gamer);
        boolean free = BoostPolicy.freeBoostAvailable(tier, gamer.getLastFreeBoostAt(), now);
        int cost = free ? 0 : BoostPolicy.BOOST_COST_COINS;

        if (cost > 0) {
            if (gamer.getCoin() < cost) {
                throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
            }
            coins.spend(gamer, cost, CoinReason.BOOST);
        } else {
            // Only stamped when the weekly one was actually spent, so a paid boost does
            // not quietly consume the free one somebody was saving.
            gamer.setLastFreeBoostAt(now);
        }

        gamer.setBoostExpiresAt(now.plus(BoostPolicy.BOOST_DURATION));
        gamerRepository.save(gamer);

        return boostStatusResponse(gamer, tier, now, cost);
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

    /** What the deck's Boost button needs to render itself without guessing. */
    @Override
    @Transactional(readOnly = true)
    public BoostResponse boostStatus(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();
        return boostStatusResponse(gamer, swipeQuota.effectiveTier(gamer), now, null);
    }

    private BoostResponse boostStatusResponse(Gamer gamer, SubscriptionTier tier, Instant now, Integer spent) {
        BoostResponseBody body = new BoostResponseBody(
                BoostPolicy.boosted(gamer.getBoostExpiresAt(), now),
                gamer.getBoostExpiresAt(),
                BoostPolicy.boostCost(tier, gamer.getLastFreeBoostAt(), now),
                BoostPolicy.freeBoostAvailable(tier, gamer.getLastFreeBoostAt(), now),
                BoostPolicy.nextFreeBoostAt(tier, gamer.getLastFreeBoostAt(), now),
                gamer.getCoin(),
                spent);

        BoostResponse response = new BoostResponse();
        response.setBody(new BaseBody<>(body));
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
     */
    private List<String> predict(Gamer gamer, Set<String> exclude) {
        String userId = gamer.getUserId();
        try {
            if (gamer.getRecommenderProfileChangedAt() != null) {
                List<String> fresh = coldStart(gamer, exclude);
                if (!fresh.isEmpty()) {
                    return fresh;
                }
                // Falls through on purpose. Empty here means the profile has nothing left
                // to rank from, and a stale ranking still beats an empty deck.
                log.debug("Stale profile for {} has nothing to rank from; using the trained vector", userId);
            }

            List<String> ranked = predictClient
                    .predict(new PredictRequest(userId, exclude, RECOMMENDATION_FETCH_SIZE))
                    .similarUsers();
            if (!ranked.isEmpty()) {
                return ranked;
            }

            log.debug("Model has no vector for {}; ranking from the profile instead", userId);
            return coldStart(gamer, exclude);
        } catch (RuntimeException e) {
            log.warn("Recommendation model unavailable for {}", userId, e);
            throw new BusinessException(TransactionCode.RECOMMENDER_SERVICE_ERROR, e);
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
    private List<String> coldStart(Gamer gamer, Set<String> exclude) {
        // Names, not ids: the model was trained on the catalogue's names and has never
        // seen our UUIDs.
        List<String> games =
                gamer.getLikedgames().stream().map(Games::getGameName).toList();
        List<String> keywords =
                gamer.getKeywords().stream().map(Keywords::getKeywordName).toList();
        if (games.isEmpty() && keywords.isEmpty()) {
            return List.of();
        }
        return predictClient
                .predictColdStart(
                        new ColdStartRequest(gamer.getUserId(), games, keywords, exclude, RECOMMENDATION_FETCH_SIZE))
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
