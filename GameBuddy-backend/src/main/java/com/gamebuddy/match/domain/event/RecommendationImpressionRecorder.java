package com.gamebuddy.match.domain.event;

import com.gamebuddy.match.infrastructure.entity.RecommendationImpression;
import com.gamebuddy.match.infrastructure.repository.RecommendationImpressionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the impression log, after the request that produced it has finished.
 *
 * <p>Same shape as {@link NotificationDispatcher}: after commit, on another thread, and
 * failures are logged rather than propagated. Losing an analytics row is a bad day for the
 * next retrain; failing a gamer's feed because a write to an append-only log went wrong is
 * a bad day for the gamer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationImpressionRecorder {

    private final RecommendationImpressionRepository impressions;
    private final Clock clock;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecommendationServed(RecommendationServedEvent event) {
        if (event.candidates().isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        try {
            List<RecommendationImpression> rows = event.candidates().stream()
                    .map(served -> new RecommendationImpression(
                            UUID.randomUUID(),
                            event.userId(),
                            served.candidateId(),
                            served.position(),
                            served.source(),
                            now))
                    .toList();
            impressions.saveAll(rows);
        } catch (RuntimeException e) {
            log.warn(
                    "Could not record {} impression(s) for {}",
                    event.candidates().size(),
                    event.userId(),
                    e);
        }
    }
}
