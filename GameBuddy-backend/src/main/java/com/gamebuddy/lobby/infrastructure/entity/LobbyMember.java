package com.gamebuddy.lobby.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One person's standing in one lobby: request, membership, or how it ended.
 *
 * <p>The composite key is the design. One row per (lobby, person) means a double request
 * has nowhere to insert itself, and REJECTED staying on the row is what makes a rejection
 * final without any extra bookkeeping — the same trick {@code content_report} plays with
 * its once-per-reporter constraint.
 *
 * <p>{@code lastReadAt} is the chat read watermark, same shape as
 * {@code ChatParticipant.lastReadAt}: null until they first open the chat, and the unread
 * count is "messages newer than this".
 */
@Entity
@Table(name = "lobby_member")
@IdClass(LobbyMember.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class LobbyMember implements Serializable {

    @Id
    @Column(name = "lobby_id", nullable = false)
    private UUID lobbyId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LobbyMemberStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    /** When the owner answered; null while PENDING. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Null until they first open the lobby chat, which means everything is unread. */
    @Column(name = "last_read_at")
    private Instant lastReadAt;

    public LobbyMember(UUID lobbyId, String userId, LobbyMemberStatus status, Instant requestedAt) {
        this.lobbyId = lobbyId;
        this.userId = userId;
        this.status = status;
        this.requestedAt = requestedAt;
    }

    /** Composite primary key. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID lobbyId;
        private String userId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(lobbyId, other.lobbyId) && Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lobbyId, userId);
        }
    }
}
