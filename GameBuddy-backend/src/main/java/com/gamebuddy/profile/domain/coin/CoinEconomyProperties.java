package com.gamebuddy.profile.domain.coin;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Every rate the coin economy runs on, in one place and settable per deployment.
 *
 * <p><strong>Why these stopped being constants.</strong> They were {@code static final}
 * fields on {@link CoinFaucet}, which meant tuning the economy — the one thing about it
 * certain to need tuning — took a recompile and a redeploy, plus a second app release to
 * stop the client's copies of the numbers from lying. The rates then drifted from the plan
 * they were written against and nobody noticed for months, because a constant leaves
 * nothing in a config file to compare against.
 *
 * <p>The defaults below are what this build was tested with, so an environment that sets
 * nothing behaves exactly as verified. Anything overridden is visible in
 * {@code application.yml} and in the environment, which is the point.
 *
 * <p><strong>What good numbers look like.</strong> An engaged free player should land near
 * 300–350 coins a week, and most of that should require doing something — watching a
 * rewarded advert, or turning up on consecutive days. Somebody who does neither should
 * still make progress, slowly. If that ratio inverts, coins stop being worth buying, and
 * selling them is the business.
 *
 * <p>Tunable at {@code gamebuddy.coins.*}; see {@code application.yml} for the environment
 * variables.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.coins")
@Getter
@Setter
public class CoinEconomyProperties {

    /**
     * What each day of an unbroken run pays, as one full cycle.
     *
     * <p>Day 8 pays what day 1 pays. That is what makes a streak worth keeping rather than
     * an annuity for opening the app: the best days exist only at the end of a run, and
     * breaking one forfeits them.
     */
    private List<Integer> dailyLadder = List.of(5, 10, 15, 20, 25, 30, 35);

    /** How long after a claim the next becomes available. */
    private Duration dailyCooldown = Duration.ofHours(24);

    /**
     * How long a run survives without a claim.
     *
     * <p>Longer than the cooldown, so a claim drifting a few hours later each day costs
     * nothing — but not so long that "daily" stops meaning daily. At 48 hours a run
     * survived on about three and a half claims a week, which is the opposite of what a
     * streak is for.
     */
    private Duration streakGrace = Duration.ofHours(36);

    /**
     * What one finished advert pays.
     *
     * <p>Above the first day of the ladder, because an advert costs the gamer something
     * real, and below what an impression earns, because otherwise every view loses money.
     */
    private int adCoins = 15;

    /** How many adverts may be paid for in a UTC day. */
    private int adDailyCap = 3;

    /** What a Gold member may claim once per {@link #stipendInterval}. */
    private int stipend = 600;

    private Duration stipendInterval = Duration.ofDays(30);

    /**
     * What one mission pays, by the band it was dealt from.
     *
     * <p>The last rate to move in here, and the one most likely to need moving. Mission pay
     * used to be {@code 25} written into an enum constructor, which is why the three weekly
     * quests were still paying their launch rate a year later — there was nothing in a
     * config file to notice.
     *
     * <p><strong>The curve rises and then stops.</strong> Set one is the easiest so it pays
     * the least; sets seven and eight are weeks of work and pay accordingly. Past the end of
     * the campaign the pool keeps dealing HARD missions at {@link #getVeteran()}, which is
     * the EASY rate — because a ladder of rising rewards that never terminates is a faucet
     * with extra steps, and difficulty alone does not cap anything for somebody determined.
     */
    private MissionRewards missionRewards = new MissionRewards();

    @Getter
    @Setter
    public static class MissionRewards {

        /** Sets 1-3. An evening each. */
        private int easy = 15;

        /** Sets 4-6. A few days each. */
        private int medium = 25;

        /** Sets 7-8. The end of the campaign. */
        private int hard = 35;

        /**
         * Every set after the campaign, forever.
         *
         * <p>Equal to {@link #easy} by default and deliberately not higher: the reward was
         * for the progression, and the progression is over. A veteran who keeps grinding
         * earns less per hour than they did in set eight, which is the asymmetry that makes
         * this bounded rather than exponential.
         */
        private int veteran = 15;
    }
}
