package com.gamebuddy.billing.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A coupon an administrator issued: some coins, or some days of Gold.
 *
 * <p><b>The code is stored in clear.</b> That is a deliberate difference from
 * {@code VerificationCode}, which is bcrypted and can never be read back, and the reason
 * is that they are not the same kind of secret. A verification code proves an address
 * belongs to whoever holds it; nobody, staff included, should be able to look one up. A
 * promotion code is a coupon — the console has to display it so it can be read out, put in
 * a newsletter or sent again, and a hashed column would make the feature's main screen
 * impossible. What protects it is the size of the space (eight characters over a
 * thirty-one symbol alphabet) and the per-account rate limit on redemption.
 *
 * <p>{@code redemptionCount} is maintained next to the redemption row by a conditional
 * UPDATE rather than being counted on demand. It is the value the redemption limit is
 * enforced against, and that enforcement has to be atomic: two people redeeming the last
 * remaining use at the same moment must not both win, and a {@code COUNT(*)} read before a
 * write is exactly how they would.
 */
@Entity
@Table(name = "promo_code", indexes = @Index(name = "idx_promo_code_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
public class PromoCode {

    @Id
    private UUID id;

    /** Uppercase, unique. Compared against whatever the gamer typed, uppercased. */
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 8)
    private PromoCodeKind kind;

    /** Set for {@link PromoCodeKind#COIN}, null otherwise. Enforced by a check constraint. */
    @Column(name = "coin_amount")
    private Integer coinAmount;

    /** Set for {@link PromoCodeKind#GOLD}, null otherwise. */
    @Column(name = "gold_days")
    private Integer goldDays;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null means unlimited. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;

    /** Non-null while switched off. Reversible, unlike deleting the row. */
    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /** Why this code exists, in the issuer's own words. For the console only. */
    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Why this code can or cannot be redeemed, in the order that answers "what do I do
     * about it": a disabled code is somebody's decision and can be undone, an expired one
     * needs a new window, an exhausted one needs a bigger limit.
     */
    public PromoCodeStatus status(Instant now) {
        if (disabledAt != null) {
            return PromoCodeStatus.DISABLED;
        }
        if (!expiresAt.isAfter(now)) {
            return PromoCodeStatus.EXPIRED;
        }
        if (isExhausted()) {
            return PromoCodeStatus.EXHAUSTED;
        }
        return PromoCodeStatus.ACTIVE;
    }

    public boolean isExhausted() {
        return maxRedemptions != null && redemptionCount >= maxRedemptions;
    }

    /** What this code hands over, for an email or a notification. */
    public String rewardText() {
        return kind == PromoCodeKind.COIN
                ? coinAmount + " coins"
                : goldDays + (goldDays == 1 ? " day of Gold" : " days of Gold");
    }
}
