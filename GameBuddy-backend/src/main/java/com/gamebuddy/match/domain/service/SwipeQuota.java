package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The free tier's daily swipe budget, and the tighter sub-cap on accepts within it.
 *
 * <p>One budget, not two. Every decision costs a swipe; an accept additionally draws on the
 * accept sub-cap. So the free tier is "50 swipes a day, 5 of which can be likes" — a
 * gamer who accepts five and declines forty-five has used fifty, not fifty-five. Two
 * independent pools would let someone run out of one while the other still had room, which
 * is a state that cannot be explained in a UI and feels arbitrary when hit.
 *
 * <p>Counted on the gamer row rather than in Redis. It is one more column write on an
 * update that already happens — both decision paths save the gamer anyway — and it survives
 * a restart, which a counter in memory does not. A cache would be faster and would also
 * mean a cache flush handed everyone a fresh allowance.
 *
 * <p>The window is per-gamer and rolling. Resetting everyone at midnight UTC would give
 * the entire user base its allowance in the same second, which is a thundering herd
 * against the feed and the recommendation model, and would drop some users' "day" in the
 * middle of their night.
 *
 * <p>Neither limit is what protects the recommender from a gamer who rejects everyone;
 * that belongs in the desirability fit, which weights raters by how much information their
 * opinions carry. A capped mass-decliner still accumulates thousands of ratings a month.
 */
@Component
@RequiredArgsConstructor
public class SwipeQuota {

    static final Duration WINDOW = Duration.ofDays(1);

    private final Clock clock;

    /**
     * Charges one decision against the budget, or refuses.
     *
     * <p>Checks the whole-budget limit before the accept sub-cap, so a gamer who is out of
     * swipes entirely is told that rather than being told they are out of likes — the two
     * lead to different places in the UI, and only one of them lets them keep browsing.
     *
     * @param accept whether this decision is an accept, which draws on the sub-cap too
     * @throws BusinessException {@link TransactionCode#SWIPE_LIMIT_REACHED} when the day's
     *     swipes are gone, or {@link TransactionCode#ACCEPT_LIMIT_REACHED} when only the
     *     likes are
     */
    public void charge(Gamer gamer, boolean accept) {
        SubscriptionTier tier = effectiveTier(gamer);
        if (tier.hasUnlimitedSwipes() && tier.hasUnlimitedAccepts()) {
            return;
        }

        Instant now = clock.instant();
        if (gamer.getQuotaResetAt() == null || !gamer.getQuotaResetAt().isAfter(now)) {
            // Lazy reset: the window rolls when the gamer next swipes, so no scheduled job
            // has to walk every row to zero a counter.
            gamer.setSwipesUsed(0);
            gamer.setAcceptsUsed(0);
            gamer.setQuotaResetAt(now.plus(WINDOW));
        }

        if (gamer.getSwipesUsed() >= tier.dailySwipes()) {
            throw new BusinessException(TransactionCode.SWIPE_LIMIT_REACHED);
        }
        if (accept && gamer.getAcceptsUsed() >= tier.dailyAccepts()) {
            throw new BusinessException(TransactionCode.ACCEPT_LIMIT_REACHED);
        }

        gamer.setSwipesUsed(gamer.getSwipesUsed() + 1);
        if (accept) {
            gamer.setAcceptsUsed(gamer.getAcceptsUsed() + 1);
        }
    }

    /**
     * Gives back one decision's worth of budget, for a swipe that is being un-made.
     *
     * <p>Floored at zero rather than trusted to be positive. The counters reset lazily, so
     * a rewind can land after the window rolled — the swipe was charged yesterday and
     * today's counter is already zero — and decrementing that would hand out a free extra
     * like every midnight.
     *
     * <p>Does not touch {@code quotaResetAt}. The window belongs to the day, not to the
     * decisions in it, and moving it would let a rewind quietly extend somebody's day.
     */
    public void refund(Gamer gamer, boolean accept) {
        gamer.setSwipesUsed(Math.max(0, gamer.getSwipesUsed() - 1));
        if (accept) {
            gamer.setAcceptsUsed(Math.max(0, gamer.getAcceptsUsed() - 1));
        }
    }

    /** What is left of both allowances, and when they return. */
    public SwipeAllowance remaining(Gamer gamer) {
        SubscriptionTier tier = effectiveTier(gamer);
        if (tier.hasUnlimitedSwipes() && tier.hasUnlimitedAccepts()) {
            return new SwipeAllowance(tier, Integer.MAX_VALUE, Integer.MAX_VALUE, true, null);
        }

        Instant now = clock.instant();
        Instant resetAt = gamer.getQuotaResetAt();
        boolean windowExpired = resetAt == null || !resetAt.isAfter(now);

        int swipesUsed = windowExpired ? 0 : gamer.getSwipesUsed();
        int acceptsUsed = windowExpired ? 0 : gamer.getAcceptsUsed();

        int swipesLeft = Math.max(0, tier.dailySwipes() - swipesUsed);
        // Accepts left can never exceed swipes left: with three swipes remaining a gamer
        // cannot make four likes, whatever the sub-cap says.
        int acceptsLeft = Math.min(swipesLeft, Math.max(0, tier.dailyAccepts() - acceptsUsed));

        return new SwipeAllowance(tier, swipesLeft, acceptsLeft, false, windowExpired ? now.plus(WINDOW) : resetAt);
    }

    public SubscriptionTier effectiveTier(Gamer gamer) {
        return SubscriptionTier.effective(
                gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), clock.instant());
    }

    /**
     * @param tier the tier actually in force
     * @param remainingSwipes decisions of any kind left in the current window
     * @param remainingAccepts how many of those may be accepts
     * @param unlimited whether the counts are meaningless because the tier is unmetered
     * @param resetsAt when the budget returns, or null when unlimited
     */
    public record SwipeAllowance(
            SubscriptionTier tier, int remainingSwipes, int remainingAccepts, boolean unlimited, Instant resetsAt) {}
}
