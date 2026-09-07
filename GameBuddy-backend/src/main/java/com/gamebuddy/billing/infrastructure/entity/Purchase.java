package com.gamebuddy.billing.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A completed in-app purchase, recorded before anything is granted.
 *
 * <p>Written first and referenced afterwards, so the row is the record of truth about what
 * a gamer paid for. Granting an entitlement without a durable record of why means a
 * refund, a chargeback or a support question has nothing to work from.
 *
 * <p>The unique constraint on {@code (platform, store_transaction_id)} is the important
 * part. Store callbacks retry, clients retry, and users background the app mid-purchase;
 * without it a single payment can be redeemed repeatedly, which is the most common way
 * mobile apps give away paid goods.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "purchase",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_purchase_store_transaction",
                        columnNames = {"platform", "store_transaction_id"}),
        indexes = @Index(name = "idx_purchase_user", columnList = "user_id"))
public class Purchase implements Serializable {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Which catalogue entry was bought; see {@code Product}. */
    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private PurchasePlatform platform;

    /**
     * The store's own identifier for this transaction.
     *
     * <p>Apple's {@code transaction_id}, Google's {@code orderId}. This is what makes
     * redemption idempotent, so it is never generated locally.
     */
    @Column(name = "store_transaction_id", nullable = false)
    private String storeTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PurchaseStatus status;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    /** For subscriptions, when the granted period ends. Null for one-off purchases. */
    @Column(name = "entitlement_expires_at")
    private Instant entitlementExpiresAt;

    /**
     * What the store actually said, kept verbatim.
     *
     * <p>Disputes are settled against the receipt, not against our interpretation of it,
     * and a verification bug found six months from now can only be diagnosed if the
     * original payload still exists.
     */
    @Column(name = "receipt", length = 4000)
    private String receipt;

    /**
     * RevenueCat's period_type: TRIAL, INTRO, NORMAL, PROMOTIONAL, PREPAID.
     *
     * <p>Without it a trial and a paid month are the same row, and neither "paywall view to
     * trial start" nor "trial to paid" can be counted at all.
     */
    @Column(name = "period_type", length = 16)
    private String periodType;

    /**
     * RevenueCat's event type: INITIAL_PURCHASE, RENEWAL, PRODUCT_CHANGE…
     *
     * <p>RENEWAL is what makes month-2 retention countable. A renewal and a first purchase
     * are otherwise indistinguishable once the row is written.
     */
    @Column(name = "event_type", length = 32)
    private String eventType;
}
