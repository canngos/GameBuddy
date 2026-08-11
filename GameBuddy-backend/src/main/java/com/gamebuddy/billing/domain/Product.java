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
    COINS_LARGE("gamebuddy.coins.3000", null, null, 3000);

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

    public static Optional<Product> byStoreId(String storeId) {
        return Arrays.stream(values()).filter(p -> p.storeId.equals(storeId)).findFirst();
    }
}
