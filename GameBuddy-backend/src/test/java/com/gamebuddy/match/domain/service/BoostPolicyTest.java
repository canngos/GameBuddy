package com.gamebuddy.match.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.enums.SubscriptionTier;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BoostPolicy")
class BoostPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    @Nested
    @DisplayName("whether a boost is running")
    class Active {

        @Test
        void neverBoostedIsNotBoosted() {
            assertFalse(BoostPolicy.boosted(null, NOW));
        }

        @Test
        @DisplayName("an expiry in the past is over, and needs nothing to have cleaned it up")
        void expiredIsOver() {
            // The whole reason this is a timestamp rather than a flag: no sweep to fail.
            assertFalse(BoostPolicy.boosted(NOW.minusSeconds(1), NOW));
            assertTrue(BoostPolicy.boosted(NOW.plusSeconds(1), NOW));
        }

        @Test
        @DisplayName("the boundary is not inclusive — an expiry of exactly now has expired")
        void boundary() {
            assertFalse(BoostPolicy.boosted(NOW, NOW));
        }
    }

    @Nested
    @DisplayName("the weekly free boost")
    class FreeBoost {

        @Test
        @DisplayName("BASIC never gets one, however long it has been")
        void basicNeverFree() {
            assertFalse(BoostPolicy.freeBoostAvailable(SubscriptionTier.BASIC, null, NOW));
            assertFalse(BoostPolicy.freeBoostAvailable(SubscriptionTier.BASIC, NOW.minus(Duration.ofDays(365)), NOW));
        }

        @Test
        @DisplayName("a member who has never used one has it waiting")
        void goldNeverUsed() {
            assertTrue(BoostPolicy.freeBoostAvailable(SubscriptionTier.GOLD, null, NOW));
        }

        @Test
        @DisplayName("it comes back after seven days, not before")
        void weeklyInterval() {
            Instant sixDaysAgo = NOW.minus(Duration.ofDays(6));
            assertFalse(BoostPolicy.freeBoostAvailable(SubscriptionTier.GOLD, sixDaysAgo, NOW));

            Instant sevenDaysAgo = NOW.minus(Duration.ofDays(7));
            assertTrue(BoostPolicy.freeBoostAvailable(SubscriptionTier.GOLD, sevenDaysAgo, NOW));
        }

        @Test
        @DisplayName("nextFreeAt is null when one is available, and a date when it is not")
        void nextFreeAt() {
            assertNull(BoostPolicy.nextFreeBoostAt(SubscriptionTier.GOLD, null, NOW));

            Instant used = NOW.minus(Duration.ofDays(2));
            assertEquals(used.plus(Duration.ofDays(7)), BoostPolicy.nextFreeBoostAt(SubscriptionTier.GOLD, used, NOW));

            // Nothing to promise somebody who does not get free boosts at all.
            assertNull(BoostPolicy.nextFreeBoostAt(SubscriptionTier.BASIC, used, NOW));
        }
    }

    @Nested
    @DisplayName("prices")
    class Prices {

        @Test
        @DisplayName("a member's free boost costs nothing, their second costs the same as anyone's")
        void boostCost() {
            assertEquals(0, BoostPolicy.boostCost(SubscriptionTier.GOLD, null, NOW));
            assertEquals(
                    BoostPolicy.BOOST_COST_COINS,
                    BoostPolicy.boostCost(SubscriptionTier.GOLD, NOW.minus(Duration.ofDays(1)), NOW));
            assertEquals(BoostPolicy.BOOST_COST_COINS, BoostPolicy.boostCost(SubscriptionTier.BASIC, null, NOW));
        }

        @Test
        @DisplayName("rewind is free on Gold and cheap otherwise")
        void rewindCost() {
            // The entitlement the analysis asks for: a reason to be a member that is not
            // just having the cap lifted.
            assertEquals(0, BoostPolicy.rewindCost(SubscriptionTier.GOLD));
            assertEquals(BoostPolicy.REWIND_COST_COINS, BoostPolicy.rewindCost(SubscriptionTier.BASIC));
        }
    }
}
