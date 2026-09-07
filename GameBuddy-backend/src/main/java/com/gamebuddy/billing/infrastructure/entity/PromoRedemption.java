package com.gamebuddy.billing.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Somebody took a code, and what they got for it.
 *
 * <p><b>Never written through {@code save()}.</b> The key is assigned rather than
 * generated, so Spring Data sees a non-null id, decides the row must already exist, and
 * issues a merge — a SELECT and then an UPDATE. No constraint is violated, nothing throws,
 * and the same code is paid out twice while the table still holds one row. The insert goes
 * through the repository's native {@code claim}, where {@code ON CONFLICT DO NOTHING}
 * makes the decision in the database and reports which caller won. That failure was
 * measured on the rewarded-ad grant before it moved to this shape.
 *
 * <p>The amounts are a snapshot, not a lookup. A code can be edited after somebody has
 * redeemed it, and what a person was actually given must not change underneath them.
 */
@Entity
@Table(
        name = "promo_redemption",
        indexes = @Index(name = "idx_promo_redemption_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class PromoRedemption {

    @EmbeddedId
    private PromoRedemptionId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 8)
    private PromoCodeKind kind;

    @Column(name = "coin_amount")
    private Integer coinAmount;

    @Column(name = "gold_days")
    private Integer goldDays;

    /** The membership expiry this redemption produced. Support evidence. */
    @Column(name = "gold_expires_at")
    private Instant goldExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Composite key, and the once-per-account guarantee itself. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromoRedemptionId implements Serializable {

        @Column(name = "code_id", nullable = false)
        private UUID codeId;

        @Column(name = "user_id", nullable = false)
        private String userId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PromoRedemptionId that)) {
                return false;
            }
            return Objects.equals(codeId, that.codeId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(codeId, userId);
        }
    }
}
