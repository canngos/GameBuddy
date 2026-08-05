package com.gamebuddy.common.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionTierTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void activeSubscriptionGrantsTheStoredTier() {
        assertEquals(
                SubscriptionTier.GOLD,
                SubscriptionTier.effective(SubscriptionTier.GOLD, NOW.plus(1, ChronoUnit.DAYS), NOW));
    }

    @Test
    @DisplayName("an expired subscription reverts to BASIC without anything having to write it back")
    void expiredSubscriptionRevertsToBasic() {
        assertEquals(
                SubscriptionTier.BASIC,
                SubscriptionTier.effective(SubscriptionTier.GOLD, NOW.minus(1, ChronoUnit.SECONDS), NOW));
    }

    @Test
    @DisplayName("a paid tier with no expiry fails closed rather than granting GOLD forever")
    void missingExpiryIsNotUnlimited() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.effective(SubscriptionTier.GOLD, null, NOW));
    }

    @Test
    void expiryExactlyNowHasLapsed() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.effective(SubscriptionTier.GOLD, NOW, NOW));
    }

    @Test
    @DisplayName("a legacy row with no tier recorded is BASIC, not null")
    void nullStoredTierIsBasic() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.effective(null, NOW.plusSeconds(60), NOW));
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.effective(null, null, NOW));
    }

    @Test
    void basicIsRationedAndGoldIsNot() {
        assertFalse(SubscriptionTier.BASIC.hasUnlimitedSwipes());
        assertFalse(SubscriptionTier.BASIC.hasUnlimitedAccepts());
        assertTrue(SubscriptionTier.GOLD.hasUnlimitedSwipes());
        assertTrue(SubscriptionTier.GOLD.hasUnlimitedAccepts());

        assertFalse(SubscriptionTier.BASIC.canSeeWhoLikedYou());
        assertTrue(SubscriptionTier.GOLD.canSeeWhoLikedYou());
    }

    @Test
    @DisplayName("accepts are a sub-cap inside the swipe budget, never a second budget beside it")
    void acceptsAreASubCapOfSwipes() {
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            assertTrue(
                    tier.dailyAccepts() <= tier.dailySwipes(),
                    tier + " must not allow more likes than swipes to spend them with");
        }
        // And on the free tier the like cap must be the tighter of the two, or it does
        // nothing: the scarce good is the accept, not the browse.
        assertTrue(SubscriptionTier.BASIC.dailyAccepts() < SubscriptionTier.BASIC.dailySwipes());
    }

    @Test
    @DisplayName("the free allowance is small but non-zero: a tier that cannot match at all is not a free tier")
    void freeTierCanStillMatch() {
        assertTrue(SubscriptionTier.BASIC.dailyAccepts() > 0);
        assertTrue(SubscriptionTier.BASIC.dailySwipes() > 0);
    }
}
