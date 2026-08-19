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

    /** A Tuesday, so week-start arithmetic has something to actually wind back. */
    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    @Nested
    @DisplayName("the daily streak")
    class Daily {

        @Test
        @DisplayName("the first ever claim is available and pays the first step")
        void firstClaim() {
            assertTrue(CoinFaucet.dailyAvailable(null, NOW));
            assertEquals(1, CoinFaucet.streakAfterClaim(0, null, NOW));
            assertEquals(5, CoinFaucet.dailyReward(1));
        }

        @Test
        @DisplayName("the reward rises to 25 and then stays there")
        void rewardLadder() {
            assertEquals(5, CoinFaucet.dailyReward(1));
            assertEquals(10, CoinFaucet.dailyReward(2));
            assertEquals(15, CoinFaucet.dailyReward(3));
            assertEquals(20, CoinFaucet.dailyReward(4));
            assertEquals(25, CoinFaucet.dailyReward(5));
            // A 200-day streak does not pay 200 coins.
            assertEquals(25, CoinFaucet.dailyReward(200));
        }

        @Test
        @DisplayName("a full week of claims is 125 coins")
        void weeklyTotal() {
            int total = 0;
            for (int day = 1; day <= 7; day++) {
                total += CoinFaucet.dailyReward(day);
            }
            assertEquals(125, total);
        }

        @Test
        @DisplayName("claiming twice in a row is refused until the cooldown passes")
        void cooldown() {
            Instant justClaimed = NOW.minus(Duration.ofHours(1));
            assertFalse(CoinFaucet.dailyAvailable(justClaimed, NOW));

            assertFalse(CoinFaucet.dailyAvailable(NOW.minus(Duration.ofHours(23)), NOW));
            assertTrue(CoinFaucet.dailyAvailable(NOW.minus(Duration.ofHours(24)), NOW));
        }

        @Test
        @DisplayName("coming back earlier the next day is refused, but the streak survives it")
        void anEarlierSessionTomorrowWaitsButKeepsTheStreak() {
            // Somebody who played at 9pm and comes back at 8pm the next day: 23 hours.
            // The cooldown is a full day now, so they are an hour early and are turned away.
            Instant lastNight = NOW.minus(Duration.ofHours(23));
            assertFalse(CoinFaucet.dailyAvailable(lastNight, NOW));

            // What protects them is the 48-hour grace, not the cooldown: whenever they do
            // claim next, the run continues rather than restarting at one.
            assertEquals(6, CoinFaucet.streakAfterClaim(5, lastNight, NOW));
        }

        @Test
        @DisplayName("a streak survives one missed evening but not a missed day")
        void streakGrace() {
            assertEquals(6, CoinFaucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(30)), NOW));
            assertEquals(6, CoinFaucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(47)), NOW));

            // Past 48 hours a day was genuinely missed.
            assertEquals(1, CoinFaucet.streakAfterClaim(5, NOW.minus(Duration.ofHours(49)), NOW));
        }

        @Test
        @DisplayName("a broken streak restarts at one, not zero")
        void brokenStreakRestartsAtOne() {
            // Claiming is always worth a day. Restarting at zero would pay nothing for
            // turning up, which is the opposite of what this is for.
            assertEquals(1, CoinFaucet.streakAfterClaim(20, NOW.minus(Duration.ofDays(10)), NOW));
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
            assertTrue(CoinFaucet.stipendAvailable(null, NOW));
            assertNull(CoinFaucet.nextStipendAt(null, NOW));
        }

        @Test
        @DisplayName("monthly, and the date is reported while it is pending")
        void monthly() {
            Instant lastWeek = NOW.minus(Duration.ofDays(7));
            assertFalse(CoinFaucet.stipendAvailable(lastWeek, NOW));
            assertEquals(lastWeek.plus(Duration.ofDays(30)), CoinFaucet.nextStipendAt(lastWeek, NOW));

            Instant longAgo = NOW.minus(Duration.ofDays(31));
            assertTrue(CoinFaucet.stipendAvailable(longAgo, NOW));
            assertNull(CoinFaucet.nextStipendAt(longAgo, NOW));
        }
    }

    @Nested
    @DisplayName("what a week is actually worth")
    class Economy {

        @Test
        @DisplayName("an engaged free player earns 200 a week; the analysis wants 250–400")
        void freePlayerWeeklyIncome() {
            int daily = 0;
            for (int day = 1; day <= 7; day++) {
                daily += CoinFaucet.dailyReward(day);
            }
            int quests = java.util.Arrays.stream(Quest.values())
                    .mapToInt(Quest::reward)
                    .sum();

            assertEquals(125, daily);
            assertEquals(75, quests);

            // 200, not the 250–400 the analysis targets. The gap is exactly the rewarded
            // video faucet (15 x 3 a day = 315 a week), which is blocked on choosing an ad
            // network. This assertion exists to fail loudly if somebody "fixes" the
            // shortfall by inflating these rates instead of shipping that faucet.
            assertEquals(200, daily + quests);
        }

        @Test
        @DisplayName("a Gold member earns 200 a week plus 600 a month")
        void memberWeeklyIncome() {
            assertEquals(600, CoinFaucet.STIPEND);
            // Roughly 340 a week all-in, which is inside the target band on its own.
            int weekly = 200 + (CoinFaucet.STIPEND * 12 / 52);
            assertTrue(weekly >= 250 && weekly <= 400, "member income out of band: " + weekly);
        }
    }
}
