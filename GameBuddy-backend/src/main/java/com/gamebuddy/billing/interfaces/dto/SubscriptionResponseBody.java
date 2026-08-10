package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the gamer currently holds.
 *
 * <p>The entitlements are sent rather than left for the client to infer from the tier
 * name. A client that hard-codes "GOLD means unlimited swipes" has to ship an update every
 * time a limit is tuned, and until it does the two disagree about what the user bought.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionResponseBody implements BaseModel {

    private String tier;

    /** Null on the free tier. */
    private Instant expiresAt;

    /** How many gamers may be accepted per day. Declining is unlimited on every tier. */
    private int dailyAccepts;

    private boolean canSeeWhoLikedYou;
    private boolean canUseAdvancedFilters;
}
