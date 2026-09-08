package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A gamer this one swiped past, and when.
 *
 * <p>An entity rather than a plain join table on {@code Gamer}, because the timestamp is
 * the whole point. Without it a pass is permanent: the recommendation ranking is a
 * deterministic function of profiles, so everyone ever declined is excluded forever and the
 * candidate pool only shrinks. A gamer who swipes enthusiastically for a week ends up with
 * an empty feed and no way back.
 *
 * <p>Declines therefore expire, after
 * {@link com.gamebuddy.match.config.MatchProperties#getDeclineRecycle()} — which is
 * configuration rather than a constant here, because the right window is a function of how
 * many gamers exist to swipe through. See that field for what a good value looks like.
 *
 * <p>Also moved off {@code Gamer} deliberately. Only the match module cares who was
 * declined, and a shared entity carrying a mapping one module uses is how the shared
 * foundation slowly accumulates every module's concerns.
 */
@Entity
@Table(
        name = "declined_matches",
        indexes = @Index(name = "idx_declined_user_time", columnList = "user_id, declined_at"))
@IdClass(DeclinedMatch.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class DeclinedMatch implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "declined_id", nullable = false)
    private String declinedId;

    @Column(name = "declined_at", nullable = false)
    private Instant declinedAt;

    public DeclinedMatch(String userId, String declinedId, Instant declinedAt) {
        this.userId = userId;
        this.declinedId = declinedId;
        this.declinedAt = declinedAt;
    }

    /** Composite primary key. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private String declinedId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(declinedId, other.declinedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, declinedId);
        }
    }
}
