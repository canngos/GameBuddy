package com.gamebuddy.shared.funnel;

/**
 * The steps of the monetisation funnel that only the client can see.
 *
 * <p>A closed set, and short on purpose. A generic event store invites the client to log
 * everything and becomes a second database nobody prunes; these are the four moments the
 * analysis actually asks about, and the endpoint refuses anything else.
 *
 * <p>All of them are safe to forge. Somebody who fakes paywall views pushes a conversion
 * rate <em>down</em>, which is a poor exploit and a self-correcting one. Every event that
 * grants something stays server-side, where it cannot be asserted by a client at all.
 */
public enum FunnelStep {

    /** The paywall was opened. Denominator of "paywall view to trial start". */
    PAYWALL_VIEWED,

    /**
     * A plan was chosen and the store sheet asked for.
     *
     * <p>Recorded before the sheet opens rather than after it returns, so an abandoned
     * purchase still counts as intent. The gap between this and a granted purchase is the
     * store's own drop-off, which is worth seeing separately from ours.
     */
    CHECKOUT_STARTED,

    /** The coin packs were reached. Says whether the currency is understood as buyable. */
    COIN_SHOP_VIEWED,

    /** A limit was hit: the daily cap, or a locked admirer. The moment demand appears. */
    PAYWALL_TRIGGERED
}
