package com.gamebuddy.shared.coin;

/**
 * Why coins moved.
 *
 * <p>Every faucet and every sink in the product, named. The set is closed on purpose: the
 * economy is only measurable if each movement can be attributed, and a free-text reason
 * turns "earned versus spent" into string archaeology within a month.
 *
 * <p>In {@code shared} because coins are spent in three modules — match buys consumables,
 * profile buys cosmetics and pays out quests, billing grants packs — and none of them may
 * depend on the others.
 */
public enum CoinReason {

    // --- Faucets -----------------------------------------------------------
    DAILY_STREAK,
    WEEKLY_QUEST,
    GOLD_STIPEND,
    BADGE_REWARD,
    /**
     * A rewarded advert watched to the end.
     *
     * <p>Granted only from AdMob's server-side verification callback, never from the app
     * saying so — see {@code RewardedAdController}. Worth separating from the others in the
     * ledger because it is the one faucet with revenue attached, so "coins paid out for
     * ads" is a number that can be put next to what AdMob actually paid.
     */
    REWARDED_AD,
    /** Bought with real money. The only faucet that is revenue rather than cost. */
    COIN_PACK,

    // --- Sinks -------------------------------------------------------------
    COSMETIC,
    /**
     * The retired deck boost. Kept because the ledger is a history: rows referencing it
     * exist and must keep resolving. Nothing writes it any more — see {@link #LOBBY_BOOST}.
     */
    BOOST,
    /** Pinning a lobby to the top of the browse list until it starts. */
    LOBBY_BOOST,
    REWIND,
    SUPER_LIKE,
    EXTRA_LIKES,
    UNLOCK_ADMIRER,

    /** A refunded coin pack, clawed back. Negative, and rare. */
    REFUND;

    /** Whether this adds coins. Sinks are the complement; there is no neutral reason. */
    public boolean isFaucet() {
        return switch (this) {
            case DAILY_STREAK, WEEKLY_QUEST, GOLD_STIPEND, BADGE_REWARD, REWARDED_AD, COIN_PACK -> true;
            case COSMETIC,
                    BOOST,
                    LOBBY_BOOST,
                    REWIND,
                    SUPER_LIKE,
                    EXTRA_LIKES,
                    UNLOCK_ADMIRER,
                    REFUND -> false;
        };
    }
}
