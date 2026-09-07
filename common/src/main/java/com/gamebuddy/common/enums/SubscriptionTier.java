package com.gamebuddy.common.enums;

import java.time.Instant;

/**
 * What a gamer has paid for.
 *
 * <p>Deliberately an ordered pair of tiers rather than a bag of independent flags. Flags
 * look flexible until two of them can be held in a combination nobody designed for, and
 * then the question "what can this account do?" has no single answer. Adding a tier here
 * is cheap; untangling overlapping entitlements later is not.
 *
 * <p><strong>The effective tier is always derived, never trusted.</strong> A stored tier
 * on its own would keep granting GOLD to everyone who ever subscribed and then cancelled,
 * because nothing would ever write BASIC back. {@link #effective(SubscriptionTier, Instant,
 * Instant)} is the only correct way to ask, and every entitlement check goes through it.
 */
public enum SubscriptionTier {

    /**
     * The free tier. Everything works; some things are rationed.
     *
     * <p>Fifteen likes a day, not five. Five was set before there was a population to
     * match against, and at this density it put the wall in front of people before their
     * first match — somebody who has never seen the product work does not upgrade, they
     * leave. The cap is a retention dial rather than a revenue lever, and the number to
     * watch after changing it is day-7 retention, not conversion.
     *
     * <p>Swipes stay at 50. The budget is one pool with a sub-cap, so 15 likes out of 50
     * decisions still asks somebody to choose.
     */
    BASIC(50, 15, false, false),

    /** The paid tier. */
    GOLD(Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);

    private final int dailySwipes;
    private final int dailyAccepts;
    private final boolean canSeeWhoLikedYou;
    private final boolean canUseAdvancedFilters;

    SubscriptionTier(int dailySwipes, int dailyAccepts, boolean canSeeWhoLikedYou, boolean canUseAdvancedFilters) {
        this.dailySwipes = dailySwipes;
        this.dailyAccepts = dailyAccepts;
        this.canSeeWhoLikedYou = canSeeWhoLikedYou;
        this.canUseAdvancedFilters = canUseAdvancedFilters;
    }

    /**
     * Total decisions per day, accepts and declines together.
     *
     * <p>One budget rather than two independent ones, so a gamer meets a single wall they
     * can reason about: "50 swipes a day, 5 of which can be likes." Two separate pools
     * would let someone run out of one while the other still had room, which is a state
     * that is impossible to explain and feels arbitrary.
     */
    public int dailySwipes() {
        return dailySwipes;
    }

    /**
     * How many of the daily swipes may be accepts. Always at most {@link #dailySwipes()}.
     *
     * <p>A sub-cap inside the swipe budget rather than a second budget beside it. The
     * accept is the scarce good — it creates a potential match, a notification and a
     * conversation slot — so it is capped far more tightly than browsing, but it is still
     * spent out of the same pool. A gamer who accepts five and declines forty-five has
     * used fifty swipes, not fifty-five.
     *
     * <p>Both numbers are launch-time settings and are meant to be tuned, not defended.
     * Rationing accepts monetises scarcity of good matches, and a new app has no density
     * yet — a gamer who runs out before finding anyone churns rather than upgrades.
     */
    public int dailyAccepts() {
        return dailyAccepts;
    }

    /** Whether the one-sided likes waiting on this gamer may be shown to them. */
    public boolean canSeeWhoLikedYou() {
        return canSeeWhoLikedYou;
    }

    /** Whether recommendations may be filtered by game, platform or region. */
    public boolean canUseAdvancedFilters() {
        return canUseAdvancedFilters;
    }

    public boolean hasUnlimitedSwipes() {
        return dailySwipes == Integer.MAX_VALUE;
    }

    public boolean hasUnlimitedAccepts() {
        return dailyAccepts == Integer.MAX_VALUE;
    }

    /**
     * The tier a gamer actually holds right now.
     *
     * @param stored what is recorded on the account, or null for a legacy row
     * @param expiresAt when the paid tier lapses; null means it never applied
     * @param now current time, injected so this is testable without waiting
     * @return {@link #BASIC} unless a paid tier is recorded <em>and</em> still in date
     */
    public static SubscriptionTier effective(SubscriptionTier stored, Instant expiresAt, Instant now) {
        if (stored == null || stored == BASIC) {
            return BASIC;
        }
        // An expiry that has passed, or was never set, means the subscription is not
        // active. Missing expiry fails closed on purpose: a paid tier with no end date is
        // a data error, and reading it as "forever" turns that error into free GOLD.
        return expiresAt != null && expiresAt.isAfter(now) ? stored : BASIC;
    }
}
