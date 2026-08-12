package com.gamebuddy.match.domain.service;

/**
 * Things bought with coins that get used up.
 *
 * <p>Cosmetics are bought once and owned forever, so a gamer who wants two frames spends
 * twice and is finished. With the faucets pouring roughly 200 coins a week in, a balance
 * that only rises makes the currency meaningless — these are what drain it.
 *
 * <p>Boost and Rewind are priced in {@link BoostPolicy} rather than here, because their
 * cost is conditional: Gold gets rewinds free and one boost a week free, and a price that
 * depends on the buyer does not belong in a flat list. Everything here costs the same to
 * everybody.
 */
public enum Consumable {

    /**
     * A like that stands out, and tells them straight away.
     *
     * <p>Priced so that a week's earnings buys two. The value is not the coins, it is that
     * an ordinary like is one of many and this one is not.
     */
    SUPER_LIKE("Super Like", 100),

    /**
     * Five more likes, today only.
     *
     * <p>Bought at exactly the moment the cap bites, which is why it is expensive relative
     * to what it gives: it competes with Gold, and it should lose that comparison for
     * anybody who hits the cap twice. Somebody buying this every evening is being told,
     * in the clearest way available, to subscribe instead.
     */
    EXTRA_LIKES("5 more likes today", 200),

    /**
     * See one person who liked you.
     *
     * <p><b>The important one.</b> It lets a gamer without Gold taste the single best thing
     * about it, at a price a week's play covers — and everybody who buys one has said, in
     * the only currency that means anything here, that they want the subscription's
     * headline feature. Every purchase is a qualified lead as well as revenue.
     *
     * <p>Cheaper than a boost on purpose. This is meant to be reachable.
     */
    UNLOCK_ADMIRER("Reveal one admirer", 150);

    /** How many likes {@link #EXTRA_LIKES} adds to today's cap. */
    public static final int EXTRA_LIKES_COUNT = 5;

    private final String title;
    private final int cost;

    Consumable(String title, int cost) {
        this.title = title;
        this.cost = cost;
    }

    public String title() {
        return title;
    }

    public int cost() {
        return cost;
    }
}
