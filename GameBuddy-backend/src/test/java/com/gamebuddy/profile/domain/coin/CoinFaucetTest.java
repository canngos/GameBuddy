package com.gamebuddy.profile.domain.coin;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.profile.domain.mission.Mission;
import com.gamebuddy.profile.domain.mission.MissionBand;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CoinFaucet")
class CoinFaucetTest {

    /** The shipped defaults, exactly as an environment that overrides nothing gets them. */
    private final CoinFaucet faucet = new CoinFaucet(new CoinEconomyProperties());

    /** A Tuesday, so week-start arithmetic has something to actually wind back. */
    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    @Nested
    @DisplayName("the daily streak")
    class Daily {

        @Test
        @DisplayName("the first ever claim is available and pays the first step")
        void firstClaim() {
            assertTrue(faucet.dailyAvailable(null, NOW));
            assertEquals(1, faucet.streakAfterClaim(0, null, NOW));
            assertEquals(5, faucet.dailyReward(1));
        }

        @Test
        @DisplayName("the ladder rises across a week and then starts the week again")
        void rewardLadder() {
            assertEquals(5, faucet.dailyReward(1));
            assertEquals(10, faucet.dailyReward(2));
            assertEquals(15, faucet.dailyReward(3));
            assertEquals(20, faucet.dailyReward(4));
            assertEquals(25, faucet.dailyReward(5));
            assertEquals(30, faucet.dailyReward(6));
            assertEquals(35, faucet.dailyReward(7));

            // The whole point of the change: day 8 starts the cycle again rather than
            // paying the top rate forever. A ladder that plateaus stops rewarding the
            // streak and becomes an annuity for opening the app.
            assertEquals(5, faucet.dailyReward(8));
            assertEquals(10, faucet.dailyReward(9));
            assertEquals(35, faucet.dailyReward(14));
            assertEquals(5, faucet.dailyReward(15));
            // And a very long streak is still worth only what its position in the cycle is.
            assertEquals(5, faucet.dailyReward(701));
        }

        @Test
        @DisplayName("a full unbroken week is 140 coins, and that is the ceiling")
        void weeklyTotal() {
            int total = 0;
            for (int day = 1; day <= 7; day++) {
                total += faucet.dailyReward(day);
            }
            assertEquals(140, total);

            // Any week is worth the same, because the cycle repeats — there is no
            // long-streak bonus to farm, only the cycle to keep.
            int secondWeek = 0;
            for (int day = 8; day <= 14; day++) {
                secondWeek += faucet.dailyReward(day);
            }
            assertEquals(total, secondWeek);
        }

        @Test
        @DisplayName("claiming twice in a row is refused until the cooldown passes")
        void cooldown() {
            Instant justClaimed = NOW.minus(Duration.ofHours(1));
            assertFalse(faucet.dailyAvailable(justClaimed, NOW));

            assertFalse(faucet.dailyAvailable(NOW.minus(Duration.ofHours(23)), NOW));
            assertTrue(faucet.dailyAvailable(NOW.minus(Duration.ofHours(24)), NOW));
        }

        @Test
        @DisplayName("coming back earlier the next day is refused, but the streak survives it")
        void anEarlierSessionTomorrowWaitsButKeepsTheStreak() {
            // Somebody who played at 9pm and comes back at 8pm the next day: 23 hours.
            // The cooldown is a full day now, so they are an hour early and are turned away.
            Instant lastNight = NOW.minus(Duration.ofHours(23));
            assertFalse(faucet.dailyAvailable(lastNight, NOW));

            // What protects them is the grace window, not the cooldown: whenever they do
            // claim next, the run continues rather than restarting at one.
            assertEquals(6, faucet.streakAfterClaim(5, lastNight, NOW));
        }

        @Test
        @DisplayName("a streak absorbs a late claim but not a skipped day")
        void streakGrace() {
            // Claiming later each day is fine — 24h cooldown, 36h grace, so there are
            // twelve hours of drift to play with.
            assertEquals(6, faucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(30)), NOW));
            assertEquals(6, faucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(35)), NOW));

            // Past 36 hours a day was genuinely skipped. It used to be 48, which let a
            // "daily" streak survive on about three and a half claims a week — the run
            // then meant nothing, which is why the window was tightened.
            assertEquals(1, faucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(37)), NOW));
            assertEquals(1, faucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(47)), NOW));
        }

        @Test
        @DisplayName("a broken streak restarts at one, not zero")
        void brokenStreakRestartsAtOne() {
            // Claiming is always worth a day. Restarting at zero would pay nothing for
            // turning up, which is the opposite of what this is for.
            assertEquals(1, faucet.streakAfterClaim(20, NOW.minus(Duration.ofDays(10)), NOW));
        }
    }

    @Nested
    @DisplayName("the mission campaign")
    class Missions {

        private final CoinEconomyProperties.MissionRewards rates = new CoinEconomyProperties().getMissionRewards();

        @Test
        @DisplayName("every band divides exactly into sets of three")
        void bandsFillWholeSets() {
            for (MissionBand band : MissionBand.values()) {
                assertEquals(
                        0,
                        Mission.inBand(band).size() % Mission.PER_SET,
                        band + " holds " + Mission.inBand(band).size()
                                + " missions, which is not a whole number of sets - the dealer would run out"
                                + " mid-set and fall back to repeats");
            }
        }

        @Test
        @DisplayName("the campaign deals every mission exactly once")
        void campaignUsesTheWholePool() {
            assertEquals(Mission.values().length, Mission.SETS * Mission.PER_SET);
            assertEquals(8, Mission.SETS);
        }

        @Test
        @DisplayName("the bands run easy, then medium, then hard - and stay hard forever")
        void bandsEscalateThenPlateau() {
            assertEquals(MissionBand.EASY, Mission.bandForSet(1));
            assertEquals(MissionBand.EASY, Mission.bandForSet(3));
            assertEquals(MissionBand.MEDIUM, Mission.bandForSet(4));
            assertEquals(MissionBand.MEDIUM, Mission.bandForSet(6));
            assertEquals(MissionBand.HARD, Mission.bandForSet(7));
            assertEquals(MissionBand.HARD, Mission.bandForSet(Mission.SETS));
            // Past the campaign the work stays hard; only the pay drops.
            assertEquals(MissionBand.HARD, Mission.bandForSet(Mission.SETS + 1));
            assertEquals(MissionBand.HARD, Mission.bandForSet(500));
        }

        @Test
        @DisplayName("the campaign ends, and everything after it is the veteran loop")
        void campaignIsFinite() {
            assertFalse(Mission.isVeteranSet(Mission.SETS));
            assertTrue(Mission.isVeteranSet(Mission.SETS + 1));
        }

        @Test
        @DisplayName("rewards rise with difficulty, and the veteran rate is the opening one")
        void rewardsEscalateAndThenReset() {
            assertTrue(rates.getEasy() < rates.getMedium(), "an easier set must not pay more");
            assertTrue(rates.getMedium() < rates.getHard(), "a harder set must pay more");
            // The whole reason the ladder is bounded. A veteran grinding HARD missions
            // forever must earn less per set than they did finishing the campaign, or a
            // ladder of rising rewards that never terminates is a faucet with extra steps.
            assertEquals(
                    rates.getEasy(),
                    rates.getVeteran(),
                    "the veteran rate must fall back to the opening rate, not stay at the peak");
            assertTrue(rates.getVeteran() < rates.getHard());
        }

        @Test
        @DisplayName("missions only ever measure something that cannot go down")
        void missionsUseCumulativeMetricsOnly() {
            // A standing metric can fall below the baseline its set was dealt at, which
            // leaves the mission stuck at zero through no fault of the player and looks
            // exactly like a bug. Badges may use these; missions may not.
            var standing = java.util.EnumSet.of(
                    com.gamebuddy.shared.badge.BadgeMetric.FRIENDS,
                    com.gamebuddy.shared.badge.BadgeMetric.COSMETICS_WORN,
                    com.gamebuddy.shared.badge.BadgeMetric.PROFILE_COMPLETENESS,
                    com.gamebuddy.shared.badge.BadgeMetric.DAILY_STREAK,
                    com.gamebuddy.shared.badge.BadgeMetric.LOBBIES_JOINED);

            for (Mission mission : Mission.values()) {
                assertFalse(
                        standing.contains(mission.getMetric()),
                        mission + " measures " + mission.getMetric() + ", which can go down");
            }
        }

        @Test
        @DisplayName("codes are unique, because a dealt row outlives the catalogue entry")
        void codesAreDistinct() {
            long distinct = java.util.Arrays.stream(Mission.values())
                    .map(Mission::getCode)
                    .distinct()
                    .count();
            assertEquals(Mission.values().length, distinct);
        }
    }

    @Nested
    @DisplayName("the Gold stipend")
    class Stipend {

        @Test
        void availableWhenNeverClaimed() {
            assertTrue(faucet.stipendAvailable(null, NOW));
            assertNull(faucet.nextStipendAt(null, NOW));
        }

        @Test
        @DisplayName("monthly, and the date is reported while it is pending")
        void monthly() {
            Instant lastWeek = NOW.minus(Duration.ofDays(7));
            assertFalse(faucet.stipendAvailable(lastWeek, NOW));
            assertEquals(lastWeek.plus(Duration.ofDays(30)), faucet.nextStipendAt(lastWeek, NOW));

            Instant longAgo = NOW.minus(Duration.ofDays(31));
            assertTrue(faucet.stipendAvailable(longAgo, NOW));
            assertNull(faucet.nextStipendAt(longAgo, NOW));
        }
    }

    @Nested
    @DisplayName("what a week is actually worth")
    class Economy {

        @Test
        @DisplayName("passive income is modest, and adverts are the bigger half")
        void freePlayerWeeklyIncome() {
            int daily = 0;
            for (int day = 1; day <= 7; day++) {
                daily += faucet.dailyReward(day);
            }
            assertEquals(140, daily);

            CoinEconomyProperties.MissionRewards rates = new CoinEconomyProperties().getMissionRewards();

            // One set a week is what the HARD targets are sized for, so this is the steady
            // state a long-lived account settles at: the campaign is behind them and the
            // pool deals at the veteran rate.
            int veteranWeek = rates.getVeteran() * Mission.PER_SET;
            assertEquals(45, veteranWeek);

            int passive = daily + veteranWeek;
            assertEquals(185, passive);

            // Adverts are the rest, and they are the half that has to be earned: the full
            // cap every day is 315 a week.
            int adCeiling = faucet.rewardedAdCoins() * faucet.rewardedAdDailyCap() * 7;
            assertEquals(315, adCeiling);
            assertEquals(500, passive + adCeiling);

            assertTrue(adCeiling > passive, "adverts must out-earn passive income, or nothing rewards activity");

            // The realistic middle for a veteran: claims their day, watches about one
            // advert. Below the 320 the old fixed quests produced, which is the direction
            // the economy was deliberately tuned in on 31 August.
            int typical = passive + (faucet.rewardedAdCoins() * 7);
            assertEquals(290, typical);
            assertTrue(typical >= 250 && typical <= 350, "veteran income out of band: " + typical);
        }

        @Test
        @DisplayName("the campaign is a one-off, and its best week stays inside the band")
        void campaignIncome() {
            CoinEconomyProperties.MissionRewards rates = new CoinEconomyProperties().getMissionRewards();

            int campaign = 0;
            for (int set = 1; set <= Mission.SETS; set++) {
                campaign += Mission.PER_SET
                        * switch (Mission.bandForSet(set)) {
                            case EASY -> rates.getEasy();
                            case MEDIUM -> rates.getMedium();
                            case HARD -> rates.getHard();
                        };
            }
            // 3x45 + 3x75 + 2x105. Paid once, ever - the same shape as the badge catalogue
            // rather than a faucet, and the reason the escalating ladder is safe.
            assertEquals(570, campaign);

            int daily = 0;
            for (int day = 1; day <= 7; day++) {
                daily += faucet.dailyReward(day);
            }

            // The most a week can be worth while the campaign is still running: a HARD set
            // finished, the streak kept, one advert a day. It touches the top of the
            // 300-350 band for a couple of weeks once in an account's life, then falls back
            // to the 290 above.
            int peak = daily + (rates.getHard() * Mission.PER_SET) + (faucet.rewardedAdCoins() * 7);
            assertEquals(350, peak);
            assertTrue(peak <= 350, "the campaign peak must not leave the band: " + peak);
        }

        @Test
        @DisplayName("a Gold member earns the passive week plus 600 a month")
        void memberWeeklyIncome() {
            assertEquals(600, faucet.stipend());
            // The stipend is a membership perk on top, not a replacement for the faucets:
            // it should be worth roughly as much again as a free player's passive week.
            int stipendPerWeek = faucet.stipend() * 12 / 52;
            assertTrue(
                    stipendPerWeek >= 100 && stipendPerWeek <= 160, "stipend per week out of band: " + stipendPerWeek);
        }
    }
}
