package com.gamebuddy.profile.domain.coin;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.profile.domain.coin.CoinFaucet.Quest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    @DisplayName("the quest week")
    class Weeks {

        @Test
        @DisplayName("the week starts on Monday, UTC")
        void mondayStart() {
            Instant start = CoinFaucet.weekStart(NOW);
            ZonedDateTime utc = start.atZone(ZoneOffset.UTC);

            assertEquals(java.time.DayOfWeek.MONDAY, utc.getDayOfWeek());
            assertEquals(0, utc.getHour());
            assertEquals(0, utc.getMinute());
            // The Monday before Tuesday 11 August 2026.
            assertEquals(Instant.parse("2026-08-10T00:00:00Z"), start);
        }

        @Test
        @DisplayName("every instant in one week gives the same start")
        void stableWithinAWeek() {
            Instant monday = Instant.parse("2026-08-10T00:00:00Z");
            Instant sundayNight = Instant.parse("2026-08-16T23:59:59Z");

            assertEquals(monday, CoinFaucet.weekStart(monday));
            assertEquals(monday, CoinFaucet.weekStart(sundayNight));
            // One second later is the next week.
            assertNotEquals(monday, CoinFaucet.weekStart(sundayNight.plusSeconds(1)));
        }

        @Test
        @DisplayName("a Monday is its own week start, not the one before")
        void mondayIsNotWoundBackAWeek() {
            Instant monday = Instant.parse("2026-08-10T09:30:00Z");
            assertEquals(Instant.parse("2026-08-10T00:00:00Z"), CoinFaucet.weekStart(monday));
        }
    }

    @Nested
    @DisplayName("quests")
    class Quests {

        @Test
        @DisplayName("all three finished is 75 coins")
        void weeklyQuestTotal() {
            int total = 0;
            for (Quest quest : Quest.values()) {
                total += quest.reward();
            }
            assertEquals(75, total);
        }

        @Test
        @DisplayName("each quest owns a distinct bit, so claiming one does not claim another")
        void distinctBits() {
            int mask = 0;
            for (Quest quest : Quest.values()) {
                assertEquals(0, mask & quest.bit(), quest + " shares a bit with another quest");
                mask |= quest.bit();
            }
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
            int quests = java.util.Arrays.stream(Quest.values())
                    .mapToInt(Quest::reward)
                    .sum();

            assertEquals(140, daily);
            assertEquals(75, quests);

            // What arrives without doing anything but turning up and finishing quests.
            int passive = daily + quests;
            assertEquals(215, passive);

            // Adverts are the rest, and they are the half that has to be earned: a full
            // cap every day is 315 a week, and a realistic two-thirds of it is ~210.
            int adCeiling = faucet.rewardedAdCoins() * faucet.rewardedAdDailyCap() * 7;
            assertEquals(315, adCeiling);

            // The ceiling, for somebody who claims everything every day and watches every
            // advert. Worth stating because it is what the shop has to be priced against.
            assertEquals(530, passive + adCeiling);

            // The property the tuning exists for: adverts are the bigger half of what is
            // available, so the fastest way to coins is doing something rather than
            // waiting. Before this change the ratio was far more lopsided still (700 of
            // 950 a week), which is what made coins accrue instead of being earned.
            assertTrue(adCeiling > passive, "adverts must out-earn passive income, or nothing rewards activity");

            // And the realistic middle: somebody who claims their day and watches about one
            // advert lands in the 300-350 band the economy is designed around. Watching the
            // full cap every day reaches 530, which is the ceiling above and is meant to
            // feel like effort rather than like the default.
            int typical = passive + (faucet.rewardedAdCoins() * 7);
            assertEquals(320, typical);
            assertTrue(typical >= 300 && typical <= 350, "typical income out of band: " + typical);
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
