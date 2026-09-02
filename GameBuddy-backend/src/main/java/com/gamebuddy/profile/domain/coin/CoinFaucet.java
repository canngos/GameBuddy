package com.gamebuddy.profile.domain.coin;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

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
 * free player — and it is met only by somebody who turns up and watches adverts, which is
 * the point. It puts the cheapest 150-coin frame about half a week away and the 1,500-coin
 * flagship about a month away: close enough to work towards, far enough that buying coins
 * is a real shortcut rather than a formality.
 *
 * <p>The numbers themselves live in {@link CoinEconomyProperties}, not here, so the economy
 * can be retuned by a deployment instead of a release.
 */
@Component
public class CoinFaucet {

    // -- Daily streak --------------------------------------------------------

    private final CoinEconomyProperties config;

    public CoinFaucet(CoinEconomyProperties config) {
        this.config = config;
    }

    /**
     * What the next daily claim would pay, given the streak it would extend to.
     *
     * <p><strong>The ladder cycles; it does not plateau.</strong> It used to clamp at the
     * last entry, so day 8, day 80 and day 800 all paid the top rate — which meant the
     * streak stopped rewarding consecutiveness after the first week and became an
     * unconditional payment for opening the app. Cycling instead means the best days only
     * ever come at the end of an unbroken run, and breaking one costs them.
     */
    public int dailyReward(int streakAfterClaim) {
        List<Integer> ladder = config.getDailyLadder();
        int index = Math.floorMod(Math.max(1, streakAfterClaim) - 1, ladder.size());
        return ladder.get(index);
    }

    /** The whole cycle, for a client that draws it. */
    public List<Integer> dailyLadder() {
        return config.getDailyLadder();
    }

    /** Whether the daily claim is available yet. */
    public boolean dailyAvailable(Instant lastClaim, Instant now) {
        return lastClaim == null || !lastClaim.plus(config.getDailyCooldown()).isAfter(now);
    }

    public Duration dailyCooldown() {
        return config.getDailyCooldown();
    }

    /**
     * The streak a claim now would produce.
     *
     * <p>Continues an unbroken run, otherwise starts again at one. Never returns zero: a
     * claim always counts as a day, including the first one.
     */
    public int streakAfterClaim(int currentStreak, Instant lastClaim, Instant now) {
        boolean unbroken =
                lastClaim != null && lastClaim.plus(config.getStreakGrace()).isAfter(now);
        return unbroken ? currentStreak + 1 : 1;
    }

    // -- Missions ------------------------------------------------------------
    //
    // Nothing here any more. The three weekly quests were an enum in this file, priced at a
    // literal 25 and reset against a Monday-UTC week boundary that also lived here. Both
    // went with them: the catalogue is `profile/domain/mission/Mission`, the rates are
    // config under `gamebuddy.coins.mission-rewards`, and a mission set now ends when it is
    // finished rather than when the week does — so there is no shared boundary left to
    // compute. See `MissionService`.

    // -- Rewarded video ------------------------------------------------------

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

    /**
     * What one finished advert pays.
     *
     * <p>The only faucet that costs the gamer something real — half a minute they did not
     * want to spend — so it has to beat the free daily claim, and the only one that earns
     * us anything, so it must stay under what an impression is worth. A rewarded view
     * clears roughly one to two cents; the cap below is what keeps the day's total near
     * that rather than several times it.
     */
    public int rewardedAdCoins() {
        return config.getAdCoins();
    }

    /**
     * How many may be watched per day.
     *
     * <p>Capped for the gamer before us: uncapped, the fastest route to anything is
     * watching adverts in a row, which is a worse product than the one being paid for.
     */
    public int rewardedAdDailyCap() {
        return config.getAdDailyCap();
    }

    /** How many more ads this gamer may be paid for today. */
    public int rewardedAdsLeft(int watchedToday, Instant lastAdDay, Instant now) {
        boolean sameDay = lastAdDay != null && lastAdDay.equals(adDay(now));
        int cap = config.getAdDailyCap();
        return sameDay ? Math.max(0, cap - watchedToday) : cap;
    }

    // -- Gold stipend --------------------------------------------------------

    /**
     * What a Gold member is paid a month, on top of everything above.
     *
     * <p>Enough to matter, because a subscription that only removes limits gives nobody
     * anything to look forward to. It is also the cheapest retention there is: it costs
     * nothing real and it is forfeited by cancelling.
     */
    public int stipend() {
        return config.getStipend();
    }

    public boolean stipendAvailable(Instant lastClaim, Instant now) {
        return lastClaim == null || !lastClaim.plus(config.getStipendInterval()).isAfter(now);
    }

    /** When the next stipend is due, or null when one is available now. */
    public Instant nextStipendAt(Instant lastClaim, Instant now) {
        if (lastClaim == null) {
            return null;
        }
        Instant next = lastClaim.plus(config.getStipendInterval());
        return next.isAfter(now) ? next : null;
    }
}
