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
}
