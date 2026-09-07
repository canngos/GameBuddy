package com.gamebuddy.notif.domain.event;

import com.gamebuddy.notif.infrastructure.entity.NotificationOutbox;
import com.gamebuddy.notif.infrastructure.repository.NotificationOutboxRepository;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records a notification for delivery, in the transaction that asked for it.
 *
 * <p>Deliberately <strong>not</strong> an after-commit async send any more. That older
 * shape lost the notification whenever the process died between the commit and the send —
 * a pod eviction mid-deploy was enough — and left nothing behind to say one had been owed.
 *
 * <p>A plain {@code @EventListener} runs inside the publishing transaction, so the outbox
 * row and the state change that caused it commit together. If the match rolls back, so does
 * the promise to tell anyone about it. {@code NotificationOutboxPoller} does the sending
 * afterwards, where a failure is a retry rather than a loss.
 *
 * <p>The write is cheap — one insert, no network — so putting it inside the transaction
 * costs almost nothing. Sending inside the transaction, by contrast, would hold a database
 * connection open for the length of a call to Firebase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationOutboxRepository outbox;
    private final GamerRepository gamerRepository;
    private final Clock clock;

    @EventListener
    public void onNotificationRequested(NotificationRequestedEvent event) {
        if (event.fcmToken() == null || event.fcmToken().isBlank()) {
            // No device registered, or the token was detached when another account claimed
            // it. There is nothing to deliver to, and queueing it would only fail later.
            return;
        }

        if (!wanted(event)) {
            return;
        }

        Instant now = clock.instant();
        NotificationOutbox row = new NotificationOutbox();
        row.setId(UUID.randomUUID());
        row.setFcmToken(event.fcmToken());
        row.setTitle(truncate(event.title(), 200));
        row.setBody(truncate(event.body(), 1000));
        row.setKind(event.kind() == null ? "GENERAL" : event.kind().name());
        row.setTargetId(event.targetId());
        row.setCreatedAt(now);
        row.setNextAttemptAt(now);
        outbox.save(row);
    }

    /**
     * Whether the recipient still wants this category of notification.
     *
     * <p>Checked here, once, rather than at each of the nine places that raise a
     * notification. Those are spread across four modules and more will be added; a rule
     * that has to be remembered nine times is a rule that will be forgotten once, and the
     * failure mode is sending somebody exactly what they asked not to receive.
     *
     * <p>Costs one indexed lookup per notification. Worth it: this is the difference
     * between a preference and a suggestion.
     *
     * <p>An id with no account behind it is allowed through. That means the account went
     * away between the publish and this listener, and the send will fail harmlessly.
     * Refusing here would silently swallow notifications for a state we cannot distinguish
     * from a race.
     *
     * <p><strong>Looked up by id, not by token.</strong> This was
     * {@code findByFcmToken(event.fcmToken())}, which is wrong twice over. A token
     * identifies a device and an account is what holds preferences, so the question "does
     * this person want this?" was being asked of a device. And it was not even unique:
     * registration stored the client's {@code "pending"} placeholder verbatim, so every
     * account that had not yet completed push registration carried an identical token.
     * Two such accounts made this throw {@code NonUniqueResultException}, which escaped
     * through the publishing transaction and turned the caller into a 500 — accepting a
     * match failed because of a preference check. A primary-key lookup cannot be
     * ambiguous, and it is cheaper than the index scan it replaces.
     */
    private boolean wanted(NotificationRequestedEvent event) {
        if (event.kind() == null) {
            return true;
        }
        if (event.recipientId() == null) {
            return true;
        }
        return gamerRepository
                .findById(event.recipientId())
                .map(gamer -> event.kind().category().wantedBy(gamer))
                .orElse(true);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
