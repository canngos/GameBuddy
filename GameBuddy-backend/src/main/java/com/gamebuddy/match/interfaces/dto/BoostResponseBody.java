package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Boost state, and everything the button needs to describe itself.
 *
 * <p>Serves both {@code GET /match/boost} and {@code POST /match/boost} so the screen
 * renders from one shape either way. The alternative — a bare acknowledgement on the POST
 * — would leave the client guessing at the new expiry and the new balance, and guessing
 * about money is how a balance on screen stops matching the one in the database.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoostResponseBody implements BaseModel {

    /** Whether a boost is running right now. */
    private boolean active;

    /** When it ends. Null when nothing is running. */
    private Instant expiresAt;

    /** What the next boost costs in coins. Zero when the weekly free one is available. */
    private int cost;

    /** Whether that zero is because of the Gold weekly allowance. */
    private boolean freeAvailable;

    /** When the weekly free boost returns, or null when it is available now or never. */
    private Instant nextFreeAt;

    /** The balance, so a purchase does not need a second call to refresh the header. */
    private int coinBalance;

    /** What this call just spent, or null when nothing was bought. */
    private Integer coinsSpent;
}
