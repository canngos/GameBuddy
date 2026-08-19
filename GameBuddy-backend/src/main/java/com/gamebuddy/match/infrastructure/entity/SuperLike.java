package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A like that was sent as a super like.
 *
 * <p>The accept itself is recorded on {@code Gamer.approvedMatches}, and this sits beside it
 * saying only "that one was special". Kept apart from the association rather than folded into
 * it because {@code approvedMatches} is a {@code @ManyToMany}, and a JPA many-to-many join
 * table cannot carry attributes — the same wall {@link DeclinedMatch} hit with its timestamp,
 * and solved the same way.
 *
 * <p>Before this existed a super like was visible only in the moment: it decremented the
 * sender's balance and sent a different push notification, then became indistinguishable from
 * any other like. The screen where the distinction is worth the most — the list of people who
 * like you — could not show it, because nothing had kept it.
 *
 * <p>Deleted when the sender rewinds. A rewind un-makes the swipe and refunds the quota, so
 * leaving the row would mean the recipient went on being told about a like that no longer
 * exists.
 */
@Entity
@Table(name = "super_likes", indexes = @Index(name = "idx_super_like_target", columnList = "target_id"))
@IdClass(SuperLike.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class SuperLike implements Serializable {

    /** The sender. */
    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Who was super liked — and the column every read here filters on. */
    @Id
    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SuperLike(String userId, String targetId, Instant createdAt) {
        this.userId = userId;
        this.targetId = targetId;
        this.createdAt = createdAt;
    }

    /** Composite primary key. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private String targetId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(targetId, other.targetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, targetId);
        }
    }
}
