package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One admirer a gamer has paid coins to see.
 *
 * <p>Keyed by the pair rather than counted, because what was bought is the right to see a
 * <em>particular person</em>. A counter would let the same unlock be re-spent on somebody
 * else, or be lost when the list reordered — and this is the one purchase where the buyer
 * has a very clear memory of which face they paid for.
 *
 * <p>Rows survive matching, unmatching and declining. They are tiny, and charging somebody
 * twice for a face they already bought is not a trade worth the disk.
 */
@Entity
@Table(name = "unlocked_admirer")
@IdClass(UnlockedAdmirer.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnlockedAdmirer {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "admirer_id", nullable = false)
    private String admirerId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private String admirerId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return java.util.Objects.equals(userId, key.userId) && java.util.Objects.equals(admirerId, key.admirerId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, admirerId);
        }
    }
}
