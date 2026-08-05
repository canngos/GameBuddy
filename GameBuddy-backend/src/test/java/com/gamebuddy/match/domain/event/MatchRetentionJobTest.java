package com.gamebuddy.match.domain.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.match.infrastructure.entity.DeclinedMatch;
import com.gamebuddy.match.infrastructure.repository.DeclinedMatchRepository;
import com.gamebuddy.match.infrastructure.repository.RecommendationImpressionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MatchRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-08-01T03:30:00Z");

    private RecommendationImpressionRepository impressions;
    private DeclinedMatchRepository declines;
    private MatchRetentionJob job;

    @BeforeEach
    void setUp() {
        impressions = mock(RecommendationImpressionRepository.class);
        declines = mock(DeclinedMatchRepository.class);
        job = new MatchRetentionJob(impressions, declines, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(job, "retention", Duration.ofDays(90));
    }

    @Test
    void deletesImpressionsOlderThanTheRetentionWindow() {
        when(impressions.deleteServedBefore(any())).thenReturn(42);

        job.purgeExpired();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(impressions).deleteServedBefore(cutoff.capture());
        assertEquals(NOW.minus(Duration.ofDays(90)), cutoff.getValue());
    }

    @Test
    @DisplayName("a failed sweep does not kill the scheduler thread")
    void swallowsFailures() {
        when(impressions.deleteServedBefore(any())).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> job.purgeExpired());
    }

    @Test
    @DisplayName("expired declines are dropped, a day behind the window that already ignores them")
    void deletesDeclinesPastTheWindow() {
        when(declines.deleteDeclinedBefore(any())).thenReturn(7);

        job.purgeExpiredDeclines();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(declines).deleteDeclinedBefore(cutoff.capture());
        // A day of slack past the exclusion window, so the deletion stays clearly behind
        // the read cutoff instead of racing it.
        assertEquals(NOW.minus(DeclinedMatch.RECYCLE_AFTER).minus(Duration.ofDays(1)), cutoff.getValue());
    }

    @Test
    @DisplayName("the two sweeps are independent: one failing does not skip the other")
    void declineSweepSurvivesItsOwnFailure() {
        when(declines.deleteDeclinedBefore(any())).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> job.purgeExpiredDeclines());
    }
}
