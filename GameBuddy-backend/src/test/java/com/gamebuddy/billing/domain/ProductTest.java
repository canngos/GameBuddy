package com.gamebuddy.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Product")
class ProductTest {

    @Nested
    @DisplayName("byStoreId")
    class ByStoreId {

        @Test
        @DisplayName("finds a product by the exact id registered with the store")
        void exactId() {
            assertThat(Product.byStoreId("gamebuddy.gold.monthly")).contains(Product.GOLD_MONTHLY);
            assertThat(Product.byStoreId("gamebuddy.coins.1200")).contains(Product.COINS_MEDIUM);
        }

        /**
         * The regression this method exists for.
         *
         * <p>Google Play's base-plan model means RevenueCat reports a Gold renewal as
         * {@code gamebuddy.gold.monthly:monthly}. Before the suffix was stripped this returned
         * empty, RevenueCatService logged "unknown product", and somebody who had paid got
         * nothing. The ids here are the ones the Play Console actually issued.
         */
        @Test
        @DisplayName("finds a subscription when Google appends its base plan id")
        void basePlanSuffix() {
            assertThat(Product.byStoreId("gamebuddy.gold.weekly:weekly")).contains(Product.GOLD_WEEKLY);
            assertThat(Product.byStoreId("gamebuddy.gold.monthly:monthly")).contains(Product.GOLD_MONTHLY);
            assertThat(Product.byStoreId("gamebuddy.gold.yearly:yearly")).contains(Product.GOLD_YEARLY);
        }

        /**
         * A base plan is a billing arrangement, not a different thing to sell, so a second one
         * added in the Play Console later — a price experiment, say — must still grant Gold
         * rather than fall through to the unknown-product branch.
         */
        @Test
        @DisplayName("grants the same product whatever the base plan is called")
        void anyBasePlan() {
            assertThat(Product.byStoreId("gamebuddy.gold.monthly:promo-2027")).contains(Product.GOLD_MONTHLY);
        }

        @Test
        @DisplayName("does not invent a product for an id we do not sell")
        void unknown() {
            assertThat(Product.byStoreId("gamebuddy.gold.lifetime")).isEmpty();
            assertThat(Product.byStoreId("gamebuddy.gold.lifetime:monthly")).isEmpty();
            // A bare suffix must not match the first product by accident.
            assertThat(Product.byStoreId(":monthly")).isEmpty();
            assertThat(Product.byStoreId("")).isEmpty();
            assertThat(Product.byStoreId(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the catalogue")
    class Catalogue {

        /**
         * These strings are the contract with the Play Console, App Store Connect and
         * RevenueCat. A rename here that is not made in all three is a charge that grants
         * nothing, so the exact values are pinned rather than derived.
         */
        @Test
        @DisplayName("sells exactly the seven ids registered with the stores")
        void storeIds() {
            assertThat(java.util.Arrays.stream(Product.values()).map(Product::storeId))
                    .containsExactlyInAnyOrder(
                            "gamebuddy.gold.weekly",
                            "gamebuddy.gold.monthly",
                            "gamebuddy.gold.yearly",
                            "gamebuddy.coins.500",
                            "gamebuddy.coins.1200",
                            "gamebuddy.coins.3000",
                            "gamebuddy.coins.7000");
        }

        @Test
        @DisplayName("separates subscriptions from coin packs")
        void kinds() {
            assertThat(Product.GOLD_MONTHLY.isSubscription()).isTrue();
            assertThat(Product.GOLD_MONTHLY.coins()).isZero();

            assertThat(Product.COINS_LARGE.isSubscription()).isFalse();
            assertThat(Product.COINS_LARGE.coins()).isEqualTo(3000);
            assertThat(Product.COINS_LARGE.tier()).isNull();

            assertThat(Product.COINS_MEGA.isSubscription()).isFalse();
            assertThat(Product.COINS_MEGA.coins()).isEqualTo(7000);
            assertThat(Product.COINS_MEGA.tier()).isNull();
        }
    }
}
