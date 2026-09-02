package com.gamebuddy.profile.domain.mission;

/**
 * How hard a mission is, and therefore which sets it can be dealt into.
 *
 * <p>Unlike {@code BadgeTier}, a band does <strong>not</strong> carry its reward. Mission
 * pay is the one coin rate the owner is most likely to want to move without a release — it
 * is the dial that decides whether the campaign is generous or mean — so it lives in
 * {@code CoinEconomyProperties} under {@code gamebuddy.coins.mission-rewards.*} and is
 * copied onto the row at the moment a mission is dealt. Retuning it must not change the
 * price of something already sitting on somebody's screen.
 *
 * @see Mission
 */
public enum MissionBand {

    /** The first sets. Things a gamer will do this evening without being asked. */
    EASY,

    /** The middle. A few days of ordinary use each. */
    MEDIUM,

    /**
     * The end of the campaign, and everything after it.
     *
     * <p>Also the veteran loop's band: once the campaign is over the pool reshuffles and
     * keeps dealing from here, but at the EASY rate. The work stays hard and the pay does
     * not, which is what stops an endless ladder of rising rewards from being a faucet.
     */
    HARD
}
