package com.gamebuddy.match.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.enums.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What is left of BoostPolicy after the deck boost was retired: rewind pricing.
 *
 * <p>The boost cases — duration, the weekly free one, the boundary on an expiry — went with
 * the feature. Lobby boosting has no policy class of its own: it is one price and one flag,
 * and its rules are tested against {@code DefaultLobbyServiceTest} where they are enforced.
 */
@DisplayName("BoostPolicy")
class BoostPolicyTest {

    @Test
    @DisplayName("rewind is free on Gold and cheap otherwise")
    void rewindCost() {
        // The entitlement the analysis asks for: a reason to be a member that is not just
        // having the cap lifted.
        assertEquals(0, BoostPolicy.rewindCost(SubscriptionTier.GOLD));
        assertEquals(BoostPolicy.REWIND_COST_COINS, BoostPolicy.rewindCost(SubscriptionTier.BASIC));
    }
}
