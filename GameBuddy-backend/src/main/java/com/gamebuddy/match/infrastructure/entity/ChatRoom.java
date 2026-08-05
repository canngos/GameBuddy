package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One conversation between two matched gamers.
 *
 * <p>One row per conversation. The Mongo model wrote <em>two</em> documents per room — one
 * keyed sender→receiver and a mirror keyed receiver→sender, both carrying the same
 * {@code chatId} — so every room existed twice and the inbox query only ever found the
 * copy whose {@code sender} matched the caller. Two rows to represent one relationship is
 * two rows that can disagree.
 *
 * <p>{@link #pairKey} is the two user ids sorted and joined, which makes "find the room
 * for these two people" a single unique-index read regardless of who is asking.
 */
@Entity
@Table(name = "chat_room", uniqueConstraints = @UniqueConstraint(name = "uk_chat_room_pair", columnNames = "pair_key"))
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom implements Serializable {

    @Id
    private UUID id;

    /** {@code min(userA,userB) + '|' + max(userA,userB)}. Order-independent by construction. */
    @Column(name = "pair_key", nullable = false, length = 512)
    private String pairKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Builds the canonical key for a pair, in either argument order. */
    public static String pairKeyFor(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatRoom other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
