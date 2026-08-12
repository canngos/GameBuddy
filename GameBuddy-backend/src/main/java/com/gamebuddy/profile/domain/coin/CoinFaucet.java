package com.gamebuddy.profile.domain.coin;

import com.gamebuddy.shared.badge.BadgeMetric;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Where coins come from, and how much.
 *
 * <p>Built because the economy did not have a bottom. Lifetime income was 975 coins from
 * thirteen one-time badge rewards; the paid catalogue costs 9,400. A gamer who earned
 * everything the game offered could afford roughly a tenth of the shelf and then earned
 * nothing again, which quietly turns every remaining item into a coin-pack advertisement.
 *
 * <p>All the arithmetic is here rather than in the service so the rates can be read, argued
 * about and tested without a database. The target is 250–400 coins a week for an engaged
 * free player, which puts the cheapest 150-coin frame about a week away and the 1,500-coin
 * flagship about a month away — close enough to be worth working towards, far enough that
 * buying coins is still a real shortcut.
 */
public final class CoinFaucet {

    private CoinFaucet() {}

    // -- Daily streak --------------------------------------------------------

    /**
     * What each consecutive day pays, capped at the last entry.
     *
     * <p>Rising rather than flat because the point is the streak, not the coins: a fixed
     * five every day gives nobody a reason to mind breaking it. A week is 125 coins.
     */
    private static final int[] DAILY_BY_STREAK = {5, 10, 15, 20, 25, 25, 25};

    /**
     * How long after a claim the next one becomes available.
     *
     * <p>Twenty hours, not twenty-four, and this matters more than it looks. On a strict
     * day boundary somebody who plays at 9pm and then at 8pm the next day has "missed a
     * day" and loses a streak they were keeping perfectly well. Twenty hours lets the
     * habit drift earlier without punishing it.
     */
    public static final Duration DAILY_COOLDOWN = Duration.ofHours(20);

    /**
     * How long before a streak is considered broken.
     *
     * <p>Deliberately much longer than the cooldown: 48 hours means missing one evening
     * costs nothing, and only a genuinely absent day resets it.
     */
    public static final Duration STREAK_GRACE = Duration.ofHours(48);

    /** What the next daily claim would pay, given the streak it would extend to. */
    public static int dailyReward(int streakAfterClaim) {
        int index = Math.max(0, Math.min(streakAfterClaim - 1, DAILY_BY_STREAK.length - 1));
        return DAILY_BY_STREAK[index];
    }

    /** Whether the daily claim is available yet. */
    public static boolean dailyAvailable(Instant lastClaim, Instant now) {
        return lastClaim == null || !lastClaim.plus(DAILY_COOLDOWN).isAfter(now);
    }

    /**
     * The streak a claim now would produce.
     *
     * <p>Continues an unbroken run, otherwise starts again at one. Never returns zero: a
     * claim always counts as a day, including the first one.
     */
    public static int streakAfterClaim(int currentStreak, Instant lastClaim, Instant now) {
        boolean unbroken = lastClaim != null && lastClaim.plus(STREAK_GRACE).isAfter(now);
        return unbroken ? currentStreak + 1 : 1;
    }

    // -- Weekly quests -------------------------------------------------------

    /**
     * The three weekly quests. 75 coins a week if all are finished.
     *
     * <p>Each one is a metric the badge system already counts, on purpose: a quest over an
     * existing metric costs one entry here, and nothing new has to be measured, stored or
     * kept in step. They ask for the three things the product actually wants people doing —
     * talking, matching, and posting — rather than whatever is easiest to count.
     */
    public enum Quest {
        TALK("Send 10 messages", BadgeMetric.MESSAGES_SENT, 10, 25),
        MEET("Match with 2 gamers", BadgeMetric.MATCHES, 2, 25),
        POST("Write 2 posts", BadgeMetric.POSTS_WRITTEN, 2, 25);

        private final String title;
        private final BadgeMetric metric;
        private final int target;
        private final int reward;

        Quest(String title, BadgeMetric metric, int target, int reward) {
            this.title = title;
            this.metric = metric;
            this.target = target;
            this.reward = reward;
        }

        public String title() {
            return title;
        }

        public BadgeMetric metric() {
            return metric;
        }

        public int target() {
            return target;
        }

        public int reward() {
            return reward;
        }

        /** This quest's place in {@code quest_claimed_mask}. */
        public int bit() {
            return 1 << ordinal();
        }
    }

    /**
     * The start of the week a given instant falls in.
     *
     * <p>Truncated to a day and then wound back to Monday, in UTC. One boundary for
     * everybody rather than a per-gamer rolling week: a shared reset is something the whole
     * population feels at once, which is what makes "new quests" worth coming back for, and
     * it means two gamers comparing notes are talking about the same week.
     */
    public static Instant weekStart(Instant now) {
        Instant midnight = now.truncatedTo(ChronoUnit.DAYS);
        // Thursday 1 January 1970 was day 0, so day-of-week is (days + 3) mod 7 with
        // Monday as zero.
        long days = midnight.getEpochSecond() / 86400L;
        long sinceMonday = Math.floorMod(days + 3, 7);
        return midnight.minus(Duration.ofDays(sinceMonday));
    }

    // -- Rewarded video ------------------------------------------------------

    /**
     * What one finished ad pays.
     *
     * <p>Twenty, against a daily streak that starts at five. An ad is the only faucet that
     * costs the gamer something real — thirty seconds they did not want to spend — so
     * paying less than the free daily claim would make it an insult rather than an option.
     *
     * <p>It is also the only faucet that earns us money, and the rate has to stay below
     * what that is worth. A rewarded impression is worth roughly one to three cents; five a
     * day at twenty coins is 100 coins for perhaps five to fifteen cents of revenue, which
     * keeps the cheapest 150-coin frame about two days of watching away and leaves the coin
     * packs a real shortcut rather than a formality.
     */
    public static final int REWARDED_AD_COINS = 20;

    /**
     * How many may be watched per day.
     *
     * <p>Capped for the gamer's sake before ours. Uncapped, the fastest way to afford
     * anything becomes watching thirty adverts in a row, which is a worse game than the one
     * we are trying to make and burns out the ad inventory that pays for it. Five is enough
     * to be a real alternative to buying and short enough that nobody organises their
     * evening around it.
     */
    public static final int REWARDED_AD_DAILY_CAP = 5;

    /**
     * The UTC day an instant falls in, as the key the daily cap counts against.
     *
     * <p>A calendar day rather than a rolling window, unlike {@link #DAILY_COOLDOWN} above.
     * The streak cooldown exists to protect a habit from drifting; this exists only to
     * bound a total, and a bound is easier to reason about — and to explain in the app —
     * when it resets at a time everybody shares.
     */
    public static Instant adDay(Instant now) {
        return now.truncatedTo(ChronoUnit.DAYS);
    }

    /** How many more ads this gamer may be paid for today. */
    public static int rewardedAdsLeft(int watchedToday, Instant lastAdDay, Instant now) {
        boolean sameDay = lastAdDay != null && lastAdDay.equals(adDay(now));
        return sameDay ? Math.max(0, REWARDED_AD_DAILY_CAP - watchedToday) : REWARDED_AD_DAILY_CAP;
    }

    // -- Gold stipend --------------------------------------------------------

    /**
     * What a Gold member is paid a month, on top of everything above.
     *
     * <p>Enough to matter — four cheap frames, or most of a flagship — because a
     * subscription that only removes limits gives somebody nothing to look forward to. It
     * is also the cheapest retention we have: it costs us nothing real and it is forfeited
     * by cancelling.
     */
    public static final int STIPEND = 600;

    public static final Duration STIPEND_INTERVAL = Duration.ofDays(30);

    public static boolean stipendAvailable(Instant lastClaim, Instant now) {
        return lastClaim == null || !lastClaim.plus(STIPEND_INTERVAL).isAfter(now);
    }

    /** When the next stipend is due, or null when one is available now. */
    public static Instant nextStipendAt(Instant lastClaim, Instant now) {
        if (lastClaim == null) {
            return null;
        }
        Instant next = lastClaim.plus(STIPEND_INTERVAL);
        return next.isAfter(now) ? next : null;
    }
}
