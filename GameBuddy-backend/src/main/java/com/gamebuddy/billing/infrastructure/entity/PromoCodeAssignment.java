package com.gamebuddy.billing.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
 * One account a code was addressed to.
 *
 * <p>A code with no rows here is public — anybody who knows the string may redeem it. A
 * code with rows is a gift, and only the listed accounts may. The absence of rows is the
 * whole distinction; there is deliberately no "isPublic" flag, because a flag and a list
 * can disagree and then something has to decide which of them is right.
 *
 * <p>Being assigned is not the same as having redeemed: those live in separate tables
 * because the interesting questions are different. This one answers "who is still holding
 * an unused gift", which is what the reminder and the re-send are built on.
 */
@Entity
@Table(name = "promo_code_assignment", indexes = @Index(name = "idx_promo_assignment_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class PromoCodeAssignment {

    @EmbeddedId
    private PromoAssignmentId id;

    /** When the code was emailed to this account. Null means it never was. */
    @Column(name = "emailed_at")
    private Instant emailedAt;

    /** When the push went out. Null if the account had no device token at the time. */
    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Composite key: a code addresses an account at most once. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromoAssignmentId implements Serializable {

        @Column(name = "code_id", nullable = false)
        private UUID codeId;

        @Column(name = "user_id", nullable = false)
        private String userId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PromoAssignmentId that)) {
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
