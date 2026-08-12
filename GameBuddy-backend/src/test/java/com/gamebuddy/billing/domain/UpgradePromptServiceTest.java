package com.gamebuddy.billing.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * When the day-3 prompt is due, and when it must stay quiet.
 *
 * <p>Most of these assert a false. That is the point of the class: the prompt has exactly
 * one chance with each account, and every way of spending it on the wrong moment costs
 * more than the sale it was reaching for.
 */
@DisplayName("UpgradePromptService")
class UpgradePromptServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final String USER = "gamer-1";

    private GamerRepository gamers;
    private UpgradePromptService service;
    private Gamer gamer;

    @BeforeEach
    void setUp() {
        gamers = mock(GamerRepository.class);
        service = new UpgradePromptService(gamers, Clock.fixed(NOW, ZoneOffset.UTC));

        gamer = new Gamer();
        gamer.setUserId(USER);
        gamer.setSubscriptionTier(SubscriptionTier.BASIC);
        // Old enough, and matched: the one arrangement in which it should appear. Each
        // test below takes exactly one of those away.
        gamer.setCreatedDate(NOW.minus(Duration.ofDays(4)));
        when(gamers.hasMutualMatch(USER)).thenReturn(true);
    }

    @Nested
    @DisplayName("due")
    class Due {

        @Test
        @DisplayName("an account that is old enough and has matched")
        void dueOnceBothConditionsHold() {
            assertTrue(service.isDue(gamer));
        }

        @Test
        @DisplayName("exactly three days old counts as three days old")
        void boundaryIsInclusive() {
            gamer.setCreatedDate(NOW.minus(UpgradePromptService.MIN_AGE));
            assertTrue(service.isDue(gamer));
        }

        @Test
        @DisplayName("a lapsed subscriber is a prospect again")
        void lapsedSubscriberIsDue() {
            // Stored tier still says GOLD; the paid period ended yesterday. Somebody who
            // has paid before is a better prospect than a stranger, not a worse one.
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(1)));

            assertTrue(service.isDue(gamer));
        }
    }

    @Nested
    @DisplayName("not due")
    class NotDue {

        @Test
        @DisplayName("before the third day")
        void tooYoung() {
            gamer.setCreatedDate(NOW.minus(Duration.ofDays(2)));
            assertFalse(service.isDue(gamer));
        }

        @Test
        @DisplayName("when nothing has come of it yet")
        void noMatchYet() {
            when(gamers.hasMutualMatch(USER)).thenReturn(false);

            // Asking somebody to pay for more of a thing that has not worked for them once
            // is asking them to buy a promise.
            assertFalse(service.isDue(gamer));
        }

        @Test
        @DisplayName("a second time, ever")
        void onlyOnce() {
            gamer.setUpgradePromptShownAt(NOW.minus(Duration.ofDays(1)));
            assertFalse(service.isDue(gamer));
        }

        @Test
        @DisplayName("to somebody who already pays")
        void alreadySubscribed() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(20)));

            assertFalse(service.isDue(gamer));
        }

        @Test
        @DisplayName("when the signup date is unknown")
        void unknownSignupDate() {
            gamer.setCreatedDate(null);

            // Cannot tell a three-day-old account from a three-minute-old one, so it stays
            // quiet. A missed sale is the cheaper of the two mistakes.
            assertFalse(service.isDue(gamer));
        }

        @Test
        @DisplayName("the cheap checks run first, so the query is not made")
        void doesNotQueryWhenAlreadyDisqualified() {
            gamer.setUpgradePromptShownAt(NOW);

            service.isDue(gamer);

            // This runs inside the endpoint the app polls during a purchase. A disqualified
            // account must not cost a round trip to the database to say no.
            verify(gamers, never()).hasMutualMatch(any());
        }
    }

    @Nested
    @DisplayName("marking it shown")
    class MarkShown {

        @Test
        @DisplayName("stamps the row and stops it recurring")
        void stampsTheRow() {
            when(gamers.findById(USER)).thenReturn(Optional.of(gamer));

            service.markShown(USER);

            assertEquals(NOW, gamer.getUpgradePromptShownAt());
            verify(gamers).save(gamer);
            assertFalse(service.isDue(gamer));
        }

        @Test
        @DisplayName("a second report does not move the date")
        void secondReportIsIgnored() {
            Instant first = NOW.minus(Duration.ofDays(2));
            gamer.setUpgradePromptShownAt(first);
            when(gamers.findById(USER)).thenReturn(Optional.of(gamer));

            service.markShown(USER);

            // Two devices rendering the same prompt is a normal race, not a problem — and
            // rewriting the date would lose when it was actually first shown.
            assertEquals(first, gamer.getUpgradePromptShownAt());
            verify(gamers, never()).save(any());
        }

        @Test
        @DisplayName("an account that has gone does not throw")
        void unknownAccountIsIgnored() {
            when(gamers.findById(USER)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> service.markShown(USER));
            verify(gamers, never()).save(any());
        }
    }
}
