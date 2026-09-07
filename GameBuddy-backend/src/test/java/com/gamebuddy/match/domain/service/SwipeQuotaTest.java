package com.gamebuddy.match.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwipeQuotaTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final int SWIPES = SubscriptionTier.BASIC.dailySwipes();
    private static final int ACCEPTS = SubscriptionTier.BASIC.dailyAccepts();

    private static SwipeQuota quotaAt(Instant now) {
        return new SwipeQuota(Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Gamer basic() {
        Gamer gamer = new Gamer();
        gamer.setUserId("gamer-1");
        gamer.setSubscriptionTier(SubscriptionTier.BASIC);
        return gamer;
    }

    private static Gamer gold(Instant expiry) {
        Gamer gamer = basic();
        gamer.setSubscriptionTier(SubscriptionTier.GOLD);
        gamer.setSubscriptionExpiresAt(expiry);
        return gamer;
    }

    // -- one budget ----------------------------------------------------------

    @Test
    @DisplayName("accepts and declines draw on the same budget, so five plus forty-five is fifty")
    void acceptsAndDeclinesShareOneBudget() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        for (int i = 0; i < ACCEPTS; i++) {
            quota.charge(gamer, true);
        }
        for (int i = 0; i < SWIPES - ACCEPTS; i++) {
            quota.charge(gamer, false);
        }

        assertEquals(SWIPES, gamer.getSwipesUsed());
        assertEquals(ACCEPTS, gamer.getAcceptsUsed());
        // Budget exhausted: neither kind of decision is possible now.
        assertThrows(BusinessException.class, () -> quota.charge(gamer, false));
        assertThrows(BusinessException.class, () -> quota.charge(gamer, true));
    }

    @Test
    @DisplayName("declining all day never uses up the likes")
    void decliningDoesNotConsumeAccepts() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        for (int i = 0; i < SWIPES - 1; i++) {
            quota.charge(gamer, false);
        }

        assertEquals(0, gamer.getAcceptsUsed());
        assertDoesNotThrow(() -> quota.charge(gamer, true), "the last swipe can still be a like");
    }

    @Test
    @DisplayName("running out of likes still leaves the gamer able to browse")
    void acceptSubCapDoesNotStopBrowsing() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        for (int i = 0; i < ACCEPTS; i++) {
            quota.charge(gamer, true);
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> quota.charge(gamer, true));
        assertEquals(158, ex.getTransactionCode().getId(), "ACCEPT_LIMIT_REACHED");
        assertDoesNotThrow(() -> quota.charge(gamer, false), "browsing continues");
    }

    @Test
    @DisplayName("an exhausted budget reports the swipe limit, not the like limit")
    void exhaustedBudgetReportsTheSwipeLimit() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        for (int i = 0; i < SWIPES; i++) {
            quota.charge(gamer, false);
        }

        // Both codes route somewhere different in the UI, and only one of them lets the
        // gamer keep browsing, so the whole-budget check has to come first.
        BusinessException ex = assertThrows(BusinessException.class, () -> quota.charge(gamer, true));
        assertEquals(163, ex.getTransactionCode().getId(), "SWIPE_LIMIT_REACHED");
    }

    @Test
    @DisplayName("a refused decision consumes nothing, so neither counter can drift past its limit")
    void refusedDecisionIsNotCharged() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        for (int i = 0; i < ACCEPTS; i++) {
            quota.charge(gamer, true);
        }
        int swipes = gamer.getSwipesUsed();

        assertThrows(BusinessException.class, () -> quota.charge(gamer, true));
        assertThrows(BusinessException.class, () -> quota.charge(gamer, true));

        assertEquals(ACCEPTS, gamer.getAcceptsUsed());
        assertEquals(swipes, gamer.getSwipesUsed(), "a refused accept did not burn a swipe");
    }

    @Test
    void acceptsNeverExceedSwipes() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        for (int i = 0; i < ACCEPTS; i++) {
            quota.charge(gamer, true);
        }
        assertTrue(gamer.getAcceptsUsed() <= gamer.getSwipesUsed());
    }

    // -- tiers ---------------------------------------------------------------

    @Test
    void goldIsNeverCharged() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = gold(NOW.plus(Duration.ofDays(30)));

        for (int i = 0; i < 500; i++) {
            quota.charge(gamer, i % 2 == 0);
        }

        assertEquals(0, gamer.getSwipesUsed());
        assertEquals(0, gamer.getAcceptsUsed());
    }

    @Test
    @DisplayName("an expired subscription is rationed again without anything writing BASIC back")
    void expiredGoldIsRationed() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = gold(NOW.minus(Duration.ofSeconds(1)));

        for (int i = 0; i < ACCEPTS; i++) {
            quota.charge(gamer, true);
        }

        assertThrows(BusinessException.class, () -> quota.charge(gamer, true));
    }

    // -- the window ----------------------------------------------------------

    @Test
    @DisplayName("both counters roll together on the next swipe, with no scheduled job")
    void bothCountersRollTogether() {
        Gamer gamer = basic();
        SwipeQuota today = quotaAt(NOW);
        today.charge(gamer, true);
        today.charge(gamer, false);
        assertEquals(2, gamer.getSwipesUsed());
        assertEquals(1, gamer.getAcceptsUsed());

        SwipeQuota tomorrow = quotaAt(NOW.plus(Duration.ofDays(1)).plusSeconds(1));
        tomorrow.charge(gamer, false);

        assertEquals(1, gamer.getSwipesUsed());
        assertEquals(0, gamer.getAcceptsUsed());
    }

    @Test
    @DisplayName("the window is per-gamer, so it does not reset for everyone at the same instant")
    void windowIsRelativeToFirstSwipe() {
        Gamer early = basic();
        Gamer late = basic();

        quotaAt(NOW).charge(early, false);
        quotaAt(NOW.plus(Duration.ofHours(6))).charge(late, false);

        assertNotEquals(early.getQuotaResetAt(), late.getQuotaResetAt());
        assertEquals(NOW.plus(Duration.ofDays(1)), early.getQuotaResetAt());
    }

    // -- reporting -----------------------------------------------------------

    @Test
    void remainingCountsBothDown() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        quota.charge(gamer, true);
        quota.charge(gamer, false);
        quota.charge(gamer, false);

        SwipeQuota.SwipeAllowance allowance = quota.remaining(gamer);

        assertEquals(SWIPES - 3, allowance.remainingSwipes());
        assertEquals(ACCEPTS - 1, allowance.remainingAccepts());
        assertFalse(allowance.unlimited());
        assertEquals(NOW.plus(Duration.ofDays(1)), allowance.resetsAt());
    }

    @Test
    @DisplayName("accepts left is capped by swipes left: three swipes cannot buy four likes")
    void remainingAcceptsCannotExceedRemainingSwipes() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        for (int i = 0; i < SWIPES - 2; i++) {
            quota.charge(gamer, false);
        }

        SwipeQuota.SwipeAllowance allowance = quota.remaining(gamer);

        assertEquals(2, allowance.remainingSwipes());
        assertEquals(2, allowance.remainingAccepts(), "not the full sub-cap; only two swipes remain");
    }

    @Test
    @DisplayName("remaining reports a full budget once the window has passed, before any swipe resets it")
    void remainingReflectsAnExpiredWindow() {
        Gamer gamer = basic();
        quotaAt(NOW).charge(gamer, true);

        SwipeQuota.SwipeAllowance allowance =
                quotaAt(NOW.plus(Duration.ofDays(2))).remaining(gamer);

        assertEquals(SWIPES, allowance.remainingSwipes());
        assertEquals(ACCEPTS, allowance.remainingAccepts());
    }

    @Test
    void remainingIsUnlimitedForGold() {
        SwipeQuota.SwipeAllowance allowance = quotaAt(NOW).remaining(gold(NOW.plus(Duration.ofDays(5))));

        assertTrue(allowance.unlimited());
        assertNull(allowance.resetsAt());
        assertEquals(SubscriptionTier.GOLD, allowance.tier());
    }

    @Test
    void effectiveTierChecksTheExpiry() {
        assertEquals(SubscriptionTier.GOLD, quotaAt(NOW).effectiveTier(gold(NOW.plusSeconds(60))));
        assertEquals(SubscriptionTier.BASIC, quotaAt(NOW).effectiveTier(gold(NOW.minusSeconds(60))));
        assertEquals(SubscriptionTier.BASIC, quotaAt(NOW).effectiveTier(basic()));
    }

    // -- refunds, for rewind -------------------------------------------------

    @Test
    @DisplayName("a refunded like gives back both the swipe and the like")
    void refundGivesBackBoth() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        quota.charge(gamer, true);
        assertEquals(1, gamer.getSwipesUsed());
        assertEquals(1, gamer.getAcceptsUsed());

        quota.refund(gamer, true);
        assertEquals(0, gamer.getSwipesUsed());
        assertEquals(0, gamer.getAcceptsUsed());
    }

    @Test
    @DisplayName("a refunded pass gives back the swipe but touches no like")
    void refundOfDeclineLeavesAcceptsAlone() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        quota.charge(gamer, true);
        quota.charge(gamer, false);
        assertEquals(2, gamer.getSwipesUsed());
        assertEquals(1, gamer.getAcceptsUsed());

        quota.refund(gamer, false);
        assertEquals(1, gamer.getSwipesUsed());
        assertEquals(1, gamer.getAcceptsUsed());
    }

    @Test
    @DisplayName("a refund across the daily reset cannot mint a free like")
    void refundFloorsAtZero() {
        // The counters reset lazily, so a rewind can land after the window rolled: the
        // swipe was charged yesterday and today's counter is already zero. Decrementing
        // that would hand out a spare like every midnight.
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        quota.refund(gamer, true);

        assertEquals(0, gamer.getSwipesUsed());
        assertEquals(0, gamer.getAcceptsUsed());
    }

    @Test
    @DisplayName("a refund does not move the day's reset instant")
    void refundDoesNotExtendTheWindow() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        quota.charge(gamer, true);
        Instant resetAt = gamer.getQuotaResetAt();

        quota.refund(gamer, true);

        // The window belongs to the day, not to the decisions in it — moving it would let
        // a rewind quietly extend somebody's allowance.
        assertEquals(resetAt, gamer.getQuotaResetAt());
    }

    // -- bought likes --------------------------------------------------------

    @Test
    @DisplayName("bought likes raise today's cap")
    void bonusAcceptsRaiseTheCap() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();
        int cap = SubscriptionTier.BASIC.dailyAccepts();

        for (int i = 0; i < cap; i++) {
            quota.charge(gamer, true);
        }
        assertThrows(BusinessException.class, () -> quota.charge(gamer, true));

        gamer.setBonusAccepts(5);
        // Five more get through, and then the wall is back.
        for (int i = 0; i < 5; i++) {
            quota.charge(gamer, true);
        }
        assertThrows(BusinessException.class, () -> quota.charge(gamer, true));
    }

    @Test
    @DisplayName("bought likes do not survive the daily reset")
    void bonusAcceptsExpireWithTheWindow() {
        // They are sold for the evening somebody is having, not as a stockpile — and a
        // stockpile would remove any reason to buy a second one tomorrow.
        Gamer gamer = basic();
        quotaAt(NOW).charge(gamer, true);
        gamer.setBonusAccepts(5);

        quotaAt(NOW.plus(Duration.ofDays(1)).plusSeconds(1)).charge(gamer, true);

        assertEquals(0, gamer.getBonusAccepts());
    }

    @Test
    @DisplayName("remaining() counts bought likes, and stops counting them once stale")
    void remainingIncludesBonus() {
        SwipeQuota quota = quotaAt(NOW);
        Gamer gamer = basic();

        // Charged first, so the daily window is open before the bonus is granted. Order
        // matters and it is not obvious: opening the window zeroes bonusAccepts along with
        // the other counters, so a purchase written before the day has started is wiped by
        // the first swipe. DefaultMatchService.buyConsumable opens the window itself for
        // exactly this reason.
        quota.charge(gamer, true);
        gamer.setBonusAccepts(5);

        int cap = SubscriptionTier.BASIC.dailyAccepts();
        assertEquals(cap + 5 - 1, quota.remaining(gamer).remainingAccepts());

        // A day later the window has rolled and the bought ones are gone, so reporting
        // them as still available would promise likes that the next charge refuses.
        SwipeQuota tomorrow = quotaAt(NOW.plus(Duration.ofDays(1)).plusSeconds(1));
        assertEquals(cap, tomorrow.remaining(gamer).remainingAccepts());
    }
}
