package com.gamebuddy.billing.domain;

import com.gamebuddy.common.enums.SubscriptionTier;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * What can be bought.
 *
 * <p>Held in code rather than in a table on purpose. Product ids have to match strings
 * registered in App Store Connect and the Play Console exactly; a mismatch is a purchase
 * that verifies against the store and then fails to grant anything, which is the worst
 * possible failure because the user has already been charged. Keeping the list here means
 * a rename shows up as a compile error rather than as a support ticket.
 *
 * <p>Prices are not here. The stores are the authority on price — they handle currency,
 * regional pricing and tax — and a second copy in our database would drift from theirs.
 */
public enum Product {

    /**
     * One week of Gold.
     *
     * <p>The cheapest way in, and the one somebody buys to unlock filters for a weekend
     * rather than to subscribe. It was offered by the paywall before it existed here,
     * which verified against the store and then failed with {@code 160 Unknown product} —
     * exactly the charged-but-granted-nothing failure this enum exists to prevent.
     */
    GOLD_WEEKLY("gamebuddy.gold.weekly", SubscriptionTier.GOLD, Duration.ofDays(7), 0),

    /** One month of Gold. */
    GOLD_MONTHLY("gamebuddy.gold.monthly", SubscriptionTier.GOLD, Duration.ofDays(30), 0),

    /** Twelve months of Gold, sold at a discount by the store. */
    GOLD_YEARLY("gamebuddy.gold.yearly", SubscriptionTier.GOLD, Duration.ofDays(365), 0),

    /**
     * Coin packs, spendable on avatars and other cosmetics.
     *
     * <p>Coins already exist and are earned through achievements, so this sells a shortcut
     * to something the free game grants rather than something only money can reach.
     */
    COINS_SMALL("gamebuddy.coins.500", null, null, 500),
    COINS_MEDIUM("gamebuddy.coins.1200", null, null, 1200),
    COINS_LARGE("gamebuddy.coins.3000", null, null, 3000),

    /**
     * The largest pack, and the only one that buys the dearest item on the shelf outright.
     *
     * <p>Added with the 2026-09-07 reprice. Most of what a shop takes comes from a small
     * number of buyers choosing the biggest thing offered, so the top of the ladder is
     * where the ceiling on a willing buyer sits; without a rung above 3000 that ceiling was
     * ours rather than theirs. Deliberately priced below {@link #GOLD_YEARLY} — a coin pack
     * costing the same as a year of Gold invites the comparison and loses it.
     */
    COINS_MEGA("gamebuddy.coins.7000", null, null, 7000);

    private final String storeId;
    private final SubscriptionTier tier;
    private final Duration period;
    private final int coins;

    Product(String storeId, SubscriptionTier tier, Duration period, int coins) {
        this.storeId = storeId;
        this.tier = tier;
        this.period = period;
        this.coins = coins;
    }

    /** The identifier registered with Apple and Google. */
    public String storeId() {
        return storeId;
    }

    /** The tier this grants, or null for a consumable. */
    public SubscriptionTier tier() {
        return tier;
    }

    /** How long the tier lasts, or null for a consumable. */
    public Duration period() {
        return period;
    }

    /** Coins granted, or zero for a subscription. */
    public int coins() {
        return coins;
    }

    public boolean isSubscription() {
        return tier != null;
    }

    /**
     * Finds the product a store reported, tolerating Google's base-plan suffix.
     *
     * <p>Google Play's newer subscription model splits a subscription into a product and one
     * or more <em>base plans</em>, and RevenueCat identifies the pair as
     * {@code <productId>:<basePlanId>} — so a Gold renewal arrives at the webhook as
     * {@code gamebuddy.gold.monthly:monthly}, not {@code gamebuddy.gold.monthly}. An exact
     * match therefore found nothing and {@link com.gamebuddy.billing.domain.RevenueCatService}
     * logged "unknown product" while the buyer had already been charged — precisely the
     * failure the comment on {@code GOLD_WEEKLY} above describes.
     *
     * <p>The base plan is a Google-only billing arrangement, not a different thing to sell:
     * every base plan of {@code gamebuddy.gold.monthly} grants the same tier for the same
     * period. So the suffix is dropped rather than enumerated, which also means adding a base
     * plan in the Play Console (a price experiment, say) cannot break granting. Apple sends no
     * suffix and none of our ids contain a colon, so ids without one are unaffected.
     */
    public static Optional<Product> byStoreId(String storeId) {
        if (storeId == null) {
            return Optional.empty();
        }
        int suffix = storeId.indexOf(':');
        String productId = suffix < 0 ? storeId : storeId.substring(0, suffix);
        return Arrays.stream(values()).filter(p -> p.storeId.equals(productId)).findFirst();
    }
}
