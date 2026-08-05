package com.gamebuddy.notif.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A notification that has been promised but not yet delivered.
 *
 * <p>Written inside the transaction that caused it, so the promise and the state change
 * commit or roll back together. Delivery happens afterwards, from a poller.
 *
 * <p>This replaces sending directly on an after-commit thread. That was fine right up until
 * the process died between the commit and the send — a pod eviction, a rolling deploy, an
 * OOM — at which point the notification was gone with no record it had ever been owed. For
 * "you matched!" that is a shrug; the same mechanism carries anything a user paid for, and
 * there it is not.
 *
 * <p>The cost is honest: the same notification can be delivered twice if the process dies
 * between sending and marking it sent. At-least-once is the achievable guarantee, and a
 * duplicate push is a far better failure than a missing one.
 */
@Entity
@Table(
        name = "notification_outbox",
        indexes = {
            // The poller's only query: unsent and due, oldest first.
            //
            // The index actually deployed is partial — WHERE sent_at IS NULL — and lives in
            // db/schema-baseline.sql, because JPA cannot express a predicate here. That
            // matters more than it looks: the table is overwhelmingly delivered rows, and a
            // full index would be mostly entries for notifications nobody will ever query
            // again. This annotation documents the columns; the SQL owns the definition.
            @Index(name = "idx_outbox_pending", columnList = "sent_at, next_attempt_at, created_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox implements Serializable {

    @Id
    private UUID id;

    /** Device token of the recipient, resolved when the notification was requested. */
    @Column(name = "fcm_token", nullable = false, length = 512)
    private String fcmToken;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    /**
     * What the notification is about, so the client can open the right screen.
     *
     * <p>Stored as a string rather than the enum: an outbox row can outlive a deploy that
     * renames a kind, and a row that cannot be read is a notification that never arrives.
     */
    @Column(nullable = false, length = 32)
    private String kind;

    /** The gamer or post to open. Null for kinds that need no argument. */
    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null until delivered. The poller's filter, and the reason there is no status enum. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** Delivery attempts so far. Bounded; see {@code NotificationOutboxPoller}. */
    @Column(nullable = false)
    private int attempts = 0;

    /** When to try again. Backs off so a dead device does not spin. */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /**
     * Why the last attempt failed, truncated.
     *
     * <p>Kept because a row that has quietly stopped being retried needs to explain itself
     * — "gave up after five attempts" is not diagnosable without the reason.
     */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationOutbox other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
