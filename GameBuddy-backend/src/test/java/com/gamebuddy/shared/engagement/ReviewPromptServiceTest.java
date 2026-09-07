package com.gamebuddy.shared.engagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * When the Play review card may be asked for, and when it must not be.
 *
 * <p>Like {@code UpgradePromptServiceTest} next door, most of these assert a false — being
 * asked to rate an app you have barely used is the failure this class is guarding against,
 * and it is a worse one than a review never collected.
 *
 * <p>The other half of the class is about the claim being a claim: the database decides, and
 * it decides once.
 */
@DisplayName("ReviewPromptService")
class ReviewPromptServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final String USER = "gamer-1";

    private GamerRepository gamers;
    private ReviewPromptService service;
    private Gamer gamer;

    @BeforeEach
    void setUp() {
        gamers = mock(GamerRepository.class);
        service = new ReviewPromptService(gamers, Clock.fixed(NOW, ZoneOffset.UTC));

        gamer = new Gamer();
        gamer.setUserId(USER);
        // The one arrangement in which it should be asked. Each test below takes exactly one
        // of those away.
        gamer.setCreatedDate(NOW.minus(Duration.ofDays(4)));
        when(gamers.hasMutualMatches(USER, ReviewPromptService.MIN_MATCHES)).thenReturn(true);
        when(gamers.hasSentMessage(USER)).thenReturn(true);
        when(gamers.claimReviewPrompt(eq(USER), any(), any())).thenReturn(1);
    }

    @Nested
    @DisplayName("asks")
    class Asks {

        @Test
        @DisplayName("an account that is old enough, has matched twice and has spoken")
        void claimsWhenEveryConditionHolds() {
            assertTrue(service.claim(gamer));
        }

        @Test
        @DisplayName("exactly three days old counts as three days old")
        void boundaryIsInclusive() {
            gamer.setCreatedDate(NOW.minus(ReviewPromptService.MIN_AGE));
            assertTrue(service.claim(gamer));
        }

        @Test
        @DisplayName("the cutoff handed to the database is the cooldown, counted back from now")
        void cooldownIsPassedThrough() {
            service.claim(gamer);

            ArgumentCaptor<Instant> now = ArgumentCaptor.captor();
            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.captor();
            verify(gamers).claimReviewPrompt(eq(USER), now.capture(), cutoff.capture());

            assertEquals(NOW, now.getValue());
            assertEquals(NOW.minus(ReviewPromptService.COOLDOWN), cutoff.getValue());
        }
    }

    @Nested
    @DisplayName("stays quiet")
    class StaysQuiet {

        @Test
        @DisplayName("an account younger than three days")
        void tooNew() {
            gamer.setCreatedDate(NOW.minus(Duration.ofDays(2)));

            assertFalse(service.claim(gamer));
            // Refused before the database is touched at all: the two cheap facts are on the
            // principal, and somebody who signed up yesterday should not cost two queries.
            verify(gamers, never()).hasMutualMatches(any(), anyInt());
            verify(gamers, never()).claimReviewPrompt(any(), any(), any());
        }

        @Test
        @DisplayName("an account whose creation date was never recorded")
        void unknownAge() {
            gamer.setCreatedDate(null);

            assertFalse(service.claim(gamer));
            verify(gamers, never()).claimReviewPrompt(any(), any(), any());
        }

        @Test
        @DisplayName("one match is not two")
        void notEnoughMatches() {
            when(gamers.hasMutualMatches(USER, ReviewPromptService.MIN_MATCHES)).thenReturn(false);

            assertFalse(service.claim(gamer));
            verify(gamers, never()).claimReviewPrompt(any(), any(), any());
        }

        @Test
        @DisplayName("matched but never said anything")
        void neverSpoke() {
            when(gamers.hasSentMessage(USER)).thenReturn(false);

            assertFalse(service.claim(gamer));
            verify(gamers, never()).claimReviewPrompt(any(), any(), any());
        }

        @Test
        @DisplayName("asked recently: the database refuses and so does this")
        void withinTheCooldown() {
            // What a row updated inside the last ninety days looks like from here.
            when(gamers.claimReviewPrompt(eq(USER), any(), any())).thenReturn(0);

            assertFalse(service.claim(gamer));
        }

        @Test
        @DisplayName("no principal at all")
        void anonymous() {
            assertFalse(service.claim(null));
            verifyNoInteractions(gamers);
        }
    }

    @Nested
    @DisplayName("the claim is the record")
    class ClaimIsTheRecord {

        @Test
        @DisplayName("two devices racing the same moment: only one asks")
        void onlyOneCallerClaims() {
            // The conditional update is what settles it — see the repository. The loser is
            // told no and shows nothing, rather than both showing a card.
            when(gamers.claimReviewPrompt(eq(USER), any(), any())).thenReturn(1, 0);

            assertTrue(service.claim(gamer));
            assertFalse(service.claim(gamer));
        }

        @Test
        @DisplayName("a granted claim is written even though Play may never show the card")
        void writesOnTheAskNotTheShowing() {
            assertTrue(service.claim(gamer));

            // The point of the whole design: Play never reports back, so the ask is the only
            // thing either side can be sure of. Not writing here would mean asking again on
            // every dismissed match forever.
            verify(gamers).claimReviewPrompt(eq(USER), eq(NOW), any());
        }
    }
}
