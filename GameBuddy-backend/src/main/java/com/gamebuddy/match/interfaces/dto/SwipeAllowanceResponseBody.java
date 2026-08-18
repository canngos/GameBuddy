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

    /**
     * Super likes in hand. Bought rather than rationed, so no daily reset applies to it.
     *
     * <p>Here because this is the payload the deck already reads to know what it may do
     * next, and a super like is one of the things it may do. It was previously returned
     * only by the purchase call, which meant the only moment the client knew the balance
     * was the moment it changed — so the deck could not offer the action at all, and a
     * gamer who bought super likes had no way to spend them.
     */
    private int superLikes;
}
