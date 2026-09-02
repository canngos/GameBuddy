package com.gamebuddy.profile.domain.badge;

import lombok.Getter;

/**
 * How hard a badge was, and therefore what it pays.
 *
 * <p>The tier existed before this enum did — {@code badges/generate.py} has drawn a bronze,
 * silver or gold rim on every plate since the first thirteen icons — but it lived only in
 * pixels. Nothing in Java, SQL, the DTO or the app knew about it, so the one thing the art
 * was saying about difficulty could not be sorted on, filtered by, or animated.
 *
 * <p><strong>The tier sets the reward.</strong> Not a number chosen per badge: twenty-five
 * hand-picked figures drift, and the drift is invisible until somebody adds up a column and
 * finds the catalogue pays double what it was meant to — which is exactly what happened
 * before the rebalance (775 coins across ten badges, with two of them worth 150 and 200).
 * One number per tier means the only question a new badge has to answer is how hard it is.
 *
 * @see Badge
 */
@Getter
public enum BadgeTier {

    /** An afternoon. The first of anything. */
    BRONZE(25),

    /** A week or two of ordinary use. */
    SILVER(50),

    /** Months. The long-haul missions. */
    GOLD(75),

    /**
     * The hard tier, and the only one that animates.
     *
     * <p>Pays <strong>either</strong> {@link #getReward()} coins <strong>or</strong> a
     * cosmetic that cannot be bought at any price — never both. Which one a given badge
     * gives is read off {@link Badge#grantsCosmetic()}; the cosmetic itself names the badge
     * that unlocks it rather than the other way round, so there is one row to change and no
     * pair to keep in step.
     */
    PRISMATIC(125);

    /** Coins credited when a badge of this tier is claimed. */
    private final int reward;

    BadgeTier(int reward) {
        this.reward = reward;
    }
}
