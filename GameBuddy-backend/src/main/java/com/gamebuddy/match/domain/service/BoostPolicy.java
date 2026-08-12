package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.enums.SubscriptionTier;
import java.time.Duration;
import java.time.Instant;

/**
 * What Boost and Rewind cost, and who gets them free.
 *
 * <p>Both are sold for <b>coins</b> rather than as new store products. Coins are already a
 * purchasable currency with a working grant path, so this needs no new SKU in either
 * console, no new webhook mapping, and nothing from a store review. Adding
 * {@code gamebuddy.boost.single} as a real-money product later is a pricing decision, not
 * a rebuild.
 *
 * <p>The numbers are here rather than scattered through the service so that changing a
 * price is one edit, and so the tests can state the rules rather than the arithmetic.
 */
public final class BoostPolicy {

    private BoostPolicy() {}

    /**
     * How long a boost lasts.
     *
     * <p>Half an hour, because a boost is worth buying only if you can sit and answer the
     * likes it brings in. A longer one sounds more generous and mostly expires while
     * nobody is looking, which teaches people it did nothing.
     */
    public static final Duration BOOST_DURATION = Duration.ofMinutes(30);

    /** How often a Gold member gets one for nothing. */
    public static final Duration FREE_BOOST_INTERVAL = Duration.ofDays(7);

    /**
     * A boost is the most expensive thing on the shelf, and about a week and a half of a
     * free player's income. That is the intended shape: it is the one consumable whose
     * value is other people's attention, which is finite — cheap boosts would mean
     * everybody boosting, which is the same as nobody boosting.
     */
    public static final int BOOST_COST_COINS = 300;

    /**
     * Deliberately cheap.
     *
     * <p>Rewind exists to remove a specific regret — the thumb that moved before the eye
     * did — and pricing it like a feature would make people ration it and stay annoyed.
     * It is a coin sink, not a revenue line.
     */
    public static final int REWIND_COST_COINS = 50;

    /** Whether a boost is running right now. */
    public static boolean boosted(Instant boostExpiresAt, Instant now) {
        return boostExpiresAt != null && boostExpiresAt.isAfter(now);
    }

    /**
     * Whether this account's weekly free boost is available.
     *
     * <p>Gold only. A lapsed subscriber stops qualifying at the same instant they stop
     * qualifying for everything else, because the tier passed in is the effective one.
     */
    public static boolean freeBoostAvailable(SubscriptionTier tier, Instant lastFreeBoostAt, Instant now) {
        if (tier != SubscriptionTier.GOLD) {
            return false;
        }
        return lastFreeBoostAt == null
                || !lastFreeBoostAt.plus(FREE_BOOST_INTERVAL).isAfter(now);
    }

    /**
     * What a boost costs this account, in coins. Zero when the free one is available.
     */
    public static int boostCost(SubscriptionTier tier, Instant lastFreeBoostAt, Instant now) {
        return freeBoostAvailable(tier, lastFreeBoostAt, now) ? 0 : BOOST_COST_COINS;
    }

    /**
     * What a rewind costs this account, in coins.
     *
     * <p>Free on Gold. This is the entitlement the analysis asks for — a reason to be a
     * member beyond having the cap lifted — and it costs us nothing to give away.
     */
    public static int rewindCost(SubscriptionTier tier) {
        return tier == SubscriptionTier.GOLD ? 0 : REWIND_COST_COINS;
    }

    /** When the next free boost becomes available, or null if one is available now. */
    public static Instant nextFreeBoostAt(SubscriptionTier tier, Instant lastFreeBoostAt, Instant now) {
        if (tier != SubscriptionTier.GOLD || lastFreeBoostAt == null) {
            return null;
        }
        Instant next = lastFreeBoostAt.plus(FREE_BOOST_INTERVAL);
        return next.isAfter(now) ? next : null;
    }
}
