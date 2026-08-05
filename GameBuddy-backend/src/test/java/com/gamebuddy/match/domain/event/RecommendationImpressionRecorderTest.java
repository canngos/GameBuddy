package com.gamebuddy.match.domain.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.match.domain.event.RecommendationServedEvent.ServedCandidate;
import com.gamebuddy.match.infrastructure.entity.ImpressionSource;
import com.gamebuddy.match.infrastructure.entity.RecommendationImpression;
import com.gamebuddy.match.infrastructure.repository.RecommendationImpressionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationImpressionRecorderTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    private final RecommendationImpressionRepository impressions = mock(RecommendationImpressionRepository.class);
    private final RecommendationImpressionRecorder recorder =
            new RecommendationImpressionRecorder(impressions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void writesOneRowPerServedCandidate() {
        recorder.onRecommendationServed(new RecommendationServedEvent(
                "viewer",
                List.of(
                        new ServedCandidate("a", 0, ImpressionSource.MODEL),
                        new ServedCandidate("b", 1, ImpressionSource.EXPLORATION))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationImpression>> captor = ArgumentCaptor.forClass(List.class);
        verify(impressions).saveAll(captor.capture());
        List<RecommendationImpression> rows = captor.getValue();

        assertEquals(2, rows.size());
        assertEquals("viewer", rows.get(0).getUserId());
        assertEquals("a", rows.get(0).getCandidateId());
        assertEquals(0, rows.get(0).getPosition());
        assertEquals(ImpressionSource.MODEL, rows.get(0).getSource());
        assertEquals(NOW, rows.get(0).getServedAt());
        assertEquals(ImpressionSource.EXPLORATION, rows.get(1).getSource());
        assertNotNull(rows.get(0).getId(), "ids are assigned in code so the page writes as one batch");
    }

    @Test
    void writesNothingForAnEmptyPage() {
        recorder.onRecommendationServed(new RecommendationServedEvent("viewer", List.of()));
        verify(impressions, never()).saveAll(any());
    }

    @Test
    @DisplayName("a failed write is swallowed: analytics must not break the feed it measures")
    void swallowsWriteFailures() {
        when(impressions.saveAll(any())).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> recorder.onRecommendationServed(
                new RecommendationServedEvent("viewer", List.of(new ServedCandidate("a", 0, ImpressionSource.MODEL)))));
    }
}
