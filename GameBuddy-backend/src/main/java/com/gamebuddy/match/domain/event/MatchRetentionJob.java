package com.gamebuddy.match.domain.event;

import com.gamebuddy.match.infrastructure.entity.DeclinedMatch;
import com.gamebuddy.match.infrastructure.repository.DeclinedMatchRepository;
import com.gamebuddy.match.infrastructure.repository.RecommendationImpressionRepository;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trims the impression log.
 *
 * <p>This table grows by up to one row per candidate per feed open, which makes it the
 * fastest-growing table in the system by a wide margin — a few thousand active gamers
 * generate millions of rows a month. The training job only reads a recent window, so
 * everything older is storage cost and backup time with no consumer.
 *
 * <p>Deliberately a deletion job rather than an unbounded table with a "we'll deal with it
 * later" note: the later in question arrives as a disk-full page at an inconvenient hour.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRetentionJob {

    private final RecommendationImpressionRepository impressions;
    private final DeclinedMatchRepository declinedMatches;
    private final Clock clock;

    /**
     * How much history to keep. Long enough to retrain on a meaningful window and to
     * compare a new model against the period before it shipped.
     */
    @Value("${gamebuddy.impressions.retention:P90D}")
    private Duration retention;

    @Transactional
    @Scheduled(cron = "${gamebuddy.impressions.cleanup-cron:0 30 3 * * *}")
    public void purgeExpired() {
        try {
            int removed = impressions.deleteServedBefore(clock.instant().minus(retention));
            if (removed > 0) {
                log.info("Purged {} impression(s) older than {}", removed, retention);
            }
        } catch (RuntimeException e) {
            // Logged rather than rethrown: a failed cleanup should retry tomorrow, not
            // leave a scheduler thread dead.
            log.warn("Impression retention sweep failed", e);
        }
    }

    /**
     * Drops declines that have already aged out of the exclusion window.
     *
     * <p>Not correctness — the window is applied on read, so an expired row already stops
     * hiding anyone. This is housekeeping: without it the table only ever grows, one row per
     * pass per gamer forever, and the index that serves the exclusion lookup grows with it.
     *
     * <p>Separate from the impression sweep and separately transactional, so a failure in one
     * does not skip the other. The extra day of slack past the exclusion window keeps the
     * deletion clearly behind the read cutoff rather than racing it.
     */
    @Transactional
    @Scheduled(cron = "${gamebuddy.declines.cleanup-cron:0 45 3 * * *}")
    public void purgeExpiredDeclines() {
        try {
            Duration keep = DeclinedMatch.RECYCLE_AFTER.plus(DELETION_SLACK);
            int removed = declinedMatches.deleteDeclinedBefore(clock.instant().minus(keep));
            if (removed > 0) {
                log.info("Purged {} expired decline(s) older than {}", removed, keep);
            }
        } catch (RuntimeException e) {
            log.warn("Decline retention sweep failed", e);
        }
    }

    /** Kept past the exclusion window so the deletion trails the read cutoff, never races it. */
    private static final Duration DELETION_SLACK = Duration.ofDays(1);
}
