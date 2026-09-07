package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.enums.SubscriptionTier;

/**
 * What Rewind costs, and who gets it free.
 *
 * <p>Sold for <b>coins</b> rather than as a store product. Coins are already a purchasable
 * currency with a working grant path, so this needs no new SKU in either console, no new
 * webhook mapping, and nothing from a store review.
 *
 * <p><b>The name is now wider than the contents.</b> This also held the deck boost — thirty
 * minutes at the front of decks in your country, 300 coins, free weekly on Gold — which was
 * retired: testers found it hard to tell it had done anything, because what it promoted was
 * a face in a stack nobody could point at afterwards. Promotion moved to lobbies, where the
 * boosted thing is visible on a list and stays there until it starts. See
 * {@code DefaultLobbyService#boost}. The class keeps its name so the rewind pricing does not
 * churn through unrelated files; rename it when something else here needs touching anyway.
 */
public final class BoostPolicy {

    private BoostPolicy() {}

    /**
     * Deliberately cheap.
     *
     * <p>Rewind exists to remove a specific regret — the thumb that moved before the eye
     * did — and pricing it like a feature would make people ration it and stay annoyed.
     * It is a coin sink, not a revenue line.
     */
    public static final int REWIND_COST_COINS = 50;

    /**
     * What a rewind costs this account, in coins.
     *
     * <p>Free on Gold. This is the entitlement the analysis asks for — a reason to be a
     * member beyond having the cap lifted — and it costs us nothing to give away.
     */
    public static int rewindCost(SubscriptionTier tier) {
        return tier == SubscriptionTier.GOLD ? 0 : REWIND_COST_COINS;
    }
}
