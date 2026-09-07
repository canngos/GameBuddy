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

    /**
     * The three missions on screen. Never more, never fewer.
     *
     * <p>Replaced by the next three the moment all of them are claimed, in the same
     * response as the claim that finished them — so this list is never empty and the client
     * never has to ask what comes next.
     */
    private List<MissionDto> missions;

    /** Which deal this is, counting from the gamer's first. 1-based. */
    private int missionSet;

    /** How long the campaign is. The app shows "set 5 of 8" rather than a number alone. */
    private int missionSetsTotal;

    /** EASY, MEDIUM or HARD — what the three above were drawn from. */
    private String missionBand;

    /**
     * True once the campaign is finished and the pool has started repeating.
     *
     * <p>The app says so rather than hiding it. Missions stay hard and the pay drops to the
     * opening rate, and a screen that quietly paid less without explaining why would be the
     * worse of the two options.
     */
    private boolean missionVeteran;

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
