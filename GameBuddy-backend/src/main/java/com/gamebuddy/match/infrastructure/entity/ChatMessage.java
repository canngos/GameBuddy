package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One chat message, stored encrypted.
 *
 * <p>The body never reaches the database as plaintext: {@code MessageCipher} encrypts it
 * with AES-GCM before the insert and decrypts on read, so a leaked backup or a stolen disk
 * yields nothing legible. This is encryption at rest, not end-to-end — the server can still
 * decrypt, which is what makes it possible to actually review a reported message. A truly
 * end-to-end scheme would leave moderators looking at ciphertext, on a product that pairs
 * strangers and admits minors.
 *
 * <p>{@link #keyVersion} exists so the key can be rotated without rewriting the table:
 * new rows use the current key, old rows keep decrypting with the one they were written
 * under.
 *
 * <p>No per-message delivery status. Read state is a watermark on
 * {@link ChatParticipant#getLastReadAt()} instead — the Mongo model indexed a status field
 * that changed twice per message, so every RECEIVED→DELIVERED transition rewrote the row
 * and its index. At a few hundred thousand messages a day that write amplification costs
 * far more than the inserts.
 */
@Entity
@Table(
        name = "chat_message",
        indexes = {
            // Serves both "the last N messages in this room" and the inbox's "latest
            // message per room", which are the only two ways this table is ever read.
            @Index(name = "idx_chat_message_room_time", columnList = "room_id, created_at DESC, id DESC"),
            // Moderation queue. Partial in the migration; Hibernate cannot express that.
            @Index(name = "idx_chat_message_reported", columnList = "reported_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage implements Serializable {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    /** AES-GCM ciphertext. Never read directly; go through {@code MessageCipher}. */
    @Column(name = "body", nullable = false)
    private byte[] body;

    /** Per-message nonce. Reusing one across messages under the same key breaks GCM. */
    @Column(name = "nonce", nullable = false)
    private byte[] nonce;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When it was reported, or null. Null is the overwhelmingly common case. */
    @Column(name = "reported_at")
    private Instant reportedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessage other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
