package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How much of today's swipe budget this gamer has left.
 *
 * <p>One budget with a sub-cap, not two budgets: {@code remainingAccepts} is how many of
 * {@code remainingSwipes} may be likes, and is never larger than it.
 *
 * <p>Exists so the client can show the allowance before the gamer hits the wall. Finding
 * out you are rationed by being refused mid-session is how a limit turns into a
 * cancellation rather than an upgrade.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SwipeAllowanceResponseBody implements BaseModel {

    /** The tier actually in force, after checking the expiry. */
    private String tier;

    /** Decisions of any kind left today; meaningless when {@link #unlimited}. */
    private int remainingSwipes;

    /** How many of {@link #remainingSwipes} may be accepts. Never greater than it. */
    private int remainingAccepts;

    private boolean unlimited;

    /** When the budget returns, or null when unlimited. */
    private Instant resetsAt;
}
