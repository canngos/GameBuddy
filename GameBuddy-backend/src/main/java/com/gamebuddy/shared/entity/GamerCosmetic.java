package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A purchase: this gamer paid this much for this cosmetic, then.
 *
 * <p>An entity rather than a {@code @ManyToMany} on {@link Gamer} — which is how bought
 * avatars were modelled — because a join table cannot hold the price, and the price is the
 * point. Prices change; a receipt that silently changes with them is not a receipt, and
 * with real money eventually behind these coins there needs to be a record of what was
 * actually charged rather than what the item happens to cost today.
 *
 * <p>Free cosmetics never get a row. They are owned by everyone by definition, so seeding
 * one row per gamer per free item would grow this table with the product of two numbers to
 * store something the price column already says. {@code CosmeticService} treats price 0 as
 * owned; nothing else needs to know.
 *
 * <p>Kept off {@code Gamer} as a mapped collection deliberately. Loading a gamer to show
 * them on a deck card should not drag their purchase history along, and the one question
 * anybody asks of this table — "does this gamer own that item" — is a two-column lookup.
 */
@Entity
@Table(name = "gamer_cosmetic")
@IdClass(GamerCosmetic.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class GamerCosmetic implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "cosmetic_id", nullable = false)
    private UUID cosmeticId;

    /** What it cost at the time, in coins. */
    @Column(nullable = false)
    private int paid;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt = Instant.now();

    public GamerCosmetic(String userId, UUID cosmeticId, int paid) {
        this.userId = userId;
        this.cosmeticId = cosmeticId;
        this.paid = paid;
        this.acquiredAt = Instant.now();
    }

    /** Composite primary key. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private UUID cosmeticId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(cosmeticId, other.cosmeticId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, cosmeticId);
        }
    }
}
