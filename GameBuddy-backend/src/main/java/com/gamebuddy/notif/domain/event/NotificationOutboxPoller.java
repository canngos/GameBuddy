package com.gamebuddy.notif.domain.event;

import com.gamebuddy.notif.domain.service.NotificationService;
import com.gamebuddy.notif.infrastructure.entity.NotificationOutbox;
import com.gamebuddy.notif.infrastructure.repository.NotificationOutboxRepository;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers what the outbox has promised.
 *
 * <p>Runs on a short interval, claims a batch with {@code FOR UPDATE SKIP LOCKED}, and
 * sends. A row that fails is left unsent with its attempt count raised and its next attempt
 * pushed out, so a temporary Firebase problem resolves itself rather than needing anyone to
 * notice.
 *
 * <p>Attempts are bounded. A token can be permanently dead — the app uninstalled, the
 * device wiped — and retrying it forever would mean the poller spends its time on rows that
 * will never succeed while real notifications queue behind them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxPoller {

    /** How many to take per run. Small enough that one slow batch does not block the next. */
    private static final int BATCH = 50;

    /**
     * Attempts before a notification is given up on.
     *
     * <p>With the backoff below that spans roughly half an hour, which is long enough to
     * ride out an FCM incident and short enough that a dead token stops costing anything.
     */
    static final int MAX_ATTEMPTS = 5;

    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    /** Delivered rows are dropped after this; the outbox is a queue, not an audit log. */
    private static final Duration RETENTION = Duration.ofDays(3);

    private final NotificationOutboxRepository outbox;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional
    @Scheduled(fixedDelayString = "${gamebuddy.notifications.poll-interval:PT10S}")
    public void deliverPending() {
        Instant now = clock.instant();
        List<NotificationOutbox> batch = outbox.claimPending(now, PageRequest.of(0, BATCH));
        if (batch.isEmpty()) {
            return;
        }

        int sent = 0;
        int failed = 0;
        for (NotificationOutbox row : batch) {
            try {
                notifications.sendToToken(new SendNotificationTokenRequest(
                        row.getFcmToken(), row.getTitle(), row.getBody(), null, row.getKind(), row.getTargetId()));
                row.setSentAt(now);
                row.setLastError(null);
                sent++;
            } catch (RuntimeException e) {
                // One bad row must not abandon the rest of the batch, so this is caught per
                // row rather than around the loop.
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(truncate(e.getMessage()));
                row.setNextAttemptAt(now.plus(backoffFor(row.getAttempts())));
                failed++;

                if (row.getAttempts() >= MAX_ATTEMPTS) {
                    // Left unsent with its attempts spent, so it stops being claimed but is
                    // still visible. Deleting it would hide the failure entirely.
                    log.warn(
                            "Giving up on notification {} after {} attempts: {}",
                            row.getId(),
                            row.getAttempts(),
                            row.getLastError());
                }
            }
        }
        outbox.saveAll(batch);

        if (failed > 0) {
            log.info("Outbox: {} delivered, {} deferred", sent, failed);
        }
    }

    /** Exponential, so a sustained outage backs off instead of hammering. */
    private static Duration backoffFor(int attempts) {
        return BASE_BACKOFF.multipliedBy(1L << Math.min(attempts, 6));
    }

    @Transactional
    @Scheduled(cron = "${gamebuddy.notifications.cleanup-cron:0 15 3 * * *}")
    public void purgeDelivered() {
        int removed = outbox.deleteSentBefore(clock.instant().minus(RETENTION));
        if (removed > 0) {
            log.info("Outbox: purged {} delivered notification(s)", removed);
        }
        long stuck = outbox.countBySentAtIsNullAndAttemptsGreaterThanEqual(MAX_ATTEMPTS);
        if (stuck > 0) {
            // Worth surfacing: a growing number here means tokens are going stale faster
            // than they are being refreshed.
            log.warn("Outbox: {} notification(s) have exhausted their attempts", stuck);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
