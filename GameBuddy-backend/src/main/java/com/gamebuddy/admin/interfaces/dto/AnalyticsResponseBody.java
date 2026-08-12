package com.gamebuddy.admin.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Everything the console's analytics screen renders.
 *
 * <p>One response rather than a call per card. The numbers are read together, they are all
 * cheap, and a screen assembled from eight requests shows eight different moments — a
 * dashboard where "active today" and "accounts" disagree about when today was is worse
 * than one that refreshes a little less often.
 */
@Getter
@Setter
public class AnalyticsResponseBody implements BaseModel {

    /** Accounts that exist and are not the moderator, deleted ones excluded. */
    private long accounts;

    /** Of those, how many finished onboarding. The gap is the drop-off. */
    private long registered;

    private long activeToday;
    private long activeWeek;
    private long activeMonth;

    private long newToday;
    private long newWeek;

    private long banned;
    private long deleted;

    /** Currently paying: a non-BASIC tier that has not expired. */
    private long subscribers;

    /** Under-18 accounts. Not a vanity metric — it drives the compliance position. */
    private long minors;

    // --- Work waiting for the moderator ------------------------------------

    private long openReports;
    private long avatarsPending;

    /**
     * How long the oldest unanswered report has been waiting, in hours.
     *
     * <p>The terms promise 24. This is the number that says whether that promise is being
     * kept right now, which is the only form of a commitment worth making — one whose
     * state can be seen without going and looking.
     */
    private long oldestOpenReportHours;

    // --- What the product produced -----------------------------------------

    private long mutualMatches;
    private long messages;

    /** Daily signups, oldest first, with no gaps. Drives the growth graph. */
    private List<DailyPoint> growth;

    // --- The monetisation funnel -------------------------------------------
    //
    // Counts, never percentages. A rate computed from three people is noise, and a console
    // that shows "33%" without showing the three invites somebody to act on it — which is
    // exactly what will happen in the weeks after launch, when the denominators are
    // smallest and the temptation to read them is highest.

    private Funnel funnel;

    /** Day-7 retention, split by like-cap cohort. Empty until the experiment is running. */
    private List<CohortRetention> retention;

    /**
     * The funnel, over the reporting window.
     *
     * @param paywallViewers distinct accounts that opened the paywall
     * @param checkoutStarters of those, how many asked for the store sheet
     * @param trialsStarted purchases whose period_type was TRIAL
     * @param paidStarted Gold purchases that were not trials
     * @param renewals purchases arriving as a RENEWAL event
     * @param cohort30 accounts that reached 30 days old inside the window
     * @param cohort30Paid of those, how many had bought Gold by day 30
     * @param coinsEarned coins from every faucet
     * @param coinsSpent coins into every sink. Near 1:1 with earned is a healthy economy
     */
    public record Funnel(
            long paywallViewers,
            long checkoutStarters,
            long trialsStarted,
            long paidStarted,
            long renewals,
            long cohort30,
            long cohort30Paid,
            long coinsEarned,
            long coinsSpent) {}

    /**
     * One like-cap cohort's day-7 retention.
     *
     * @param cohort CONTROL or VARIANT
     * @param signups accounts old enough to have had the chance to return
     * @param retained of those, how many were active seven days in or later
     */
    public record CohortRetention(String cohort, long signups, long retained) {}

    /**
     * One day on the growth graph.
     *
     * @param date the day, in UTC
     * @param signups accounts created that day
     * @param total accounts created on or before that day, within the window
     */
    public record DailyPoint(LocalDate date, long signups, long total) {}
}
