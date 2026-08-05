package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One gamer's side of one conversation, and how far they have read.
 *
 * <p>This replaces the per-message {@code MessageStatus} column. A watermark is one small
 * row per person per room, updated when they open the conversation; the unread count is
 * "messages in this room newer than my watermark". The old model wrote a status onto every
 * individual message and indexed it, so a fifty-message conversation being opened issued
 * fifty row updates and fifty index updates to answer a question one timestamp answers.
 *
 * <p>It also survives a subtlety the status column got wrong: marking messages DELIVERED
 * required knowing which ones had not been, which meant reading them all first.
 */
@Entity
@Table(name = "chat_participant")
@IdClass(ChatParticipant.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatParticipant implements Serializable {

    @Id
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Null until they first open the conversation, which means everything is unread. */
    @Column(name = "last_read_at")
    private Instant lastReadAt;

    public ChatParticipant(UUID roomId, String userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    /** Composite primary key. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID roomId;
        private String userId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(roomId, other.roomId) && Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, userId);
        }
    }
}
