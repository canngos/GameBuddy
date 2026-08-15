package com.gamebuddy.lobby.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An open game lobby: one game, one owner, up to five players, a planned time.
 *
 * <p>The owner is a plain id rather than a {@code @ManyToOne Gamer}, the same choice
 * {@code ChatMessage} makes for its sender: the lifecycle sweeper updates these rows in
 * bulk, and nothing on this entity needs to walk into the owner's row to do its job.
 * Screens that show the owner's name resolve it through {@code GamerRepository} like every
 * other roster entry.
 *
 * <p>{@code @Version} because the interesting races are real: the owner accepting the last
 * seat while the sweeper cancels, or two accepts filling one slot. The loser of the lock
 * surfaces {@code CONCURRENT_MODIFICATION} and nothing is half-applied.
 *
 * <p>One live lobby per owner is enforced by a partial unique index
 * ({@code uq_lobby_active_owner}) that JPA cannot express — it exists only in the SQL. The
 * service checks first and answers {@code LOBBY_LIMIT_REACHED}; the index is the backstop
 * for the race window.
 */
@Entity
@Table(name = "lobby")
@Getter
@Setter
@NoArgsConstructor
public class Lobby implements Serializable {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 500)
    private String description;

    /** Free text: mic, rank, in-game chat. Informational — the owner screens by hand. */
    @Column(length = 300)
    private String requirements;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LobbyTone tone;

    /** Including the owner. 2–5, checked in the request and again by the database. */
    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    /** The planned start, per the owner. The sweeper reads it generously, never punctually. */
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LobbyStatus status = LobbyStatus.OPEN;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isOwnedBy(String userId) {
        return Objects.equals(ownerId, userId);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Lobby other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
