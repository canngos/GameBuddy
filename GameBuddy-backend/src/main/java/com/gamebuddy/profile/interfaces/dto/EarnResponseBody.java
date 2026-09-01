package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Everything the earn screen shows, and everything a claim returns.
 *
 * <p>One shape for both, so a claim leaves the screen correct without a second request. The
 * balance is included for the same reason: after taking coins the number in the Market
 * header is wrong, and the client should not have to guess the new one by adding.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EarnResponseBody implements BaseModel {

    private boolean dailyAvailable;

    /** What claiming now would pay, at the streak it would reach. */
    private int dailyReward;

    /** Consecutive days already claimed. */
    private int streak;

    /** When the daily becomes available, or null when it is available now or never taken. */
    private Instant dailyReadyAt;

    private List<QuestDto> quests;

    private boolean stipendAvailable;
    private int stipendAmount;

    /** When the next stipend is due, or null when available now or not a member. */
    private Instant stipendReadyAt;

    private int coinBalance;

    /**
     * Rewarded adverts this gamer may still be paid for today.
     *
     * <p>Sent even when it is zero, so the card can say "back tomorrow" rather than
     * disappearing — a faucet that vanishes when it is spent looks like a bug, and the
     * gamer has no way to learn it exists again.
     */
    private int adsLeftToday;

    /**
     * The streak cycle, so the strip draws what the server actually pays.
     *
     * <p>The app keeps a copy only as a render fallback for a response that predates this
     * field; the server is the authority.
     */
    private java.util.List<Integer> dailyLadder;

    /** What one finished advert pays. */
    private int adCoins;
}
