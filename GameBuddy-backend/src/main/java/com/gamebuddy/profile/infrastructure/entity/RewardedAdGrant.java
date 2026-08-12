package com.gamebuddy.profile.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rewarded-ad callback that has already been paid out.
 *
 * <p>Keyed by AdMob's {@code transaction_id} rather than by an id of our own, and that is
 * the point: their value is identical across every retry of the same reward, so inserting
 * it is what makes paying twice impossible.
 *
 * <p>AdMob retries any callback that does not answer 200, so repeats are the normal case
 * rather than an attack — though a replayed capture of the signed URL looks exactly the
 * same from here, and is stopped by the same constraint.
 */
@Entity
@Table(name = "rewarded_ad_grant")
@Getter
@Setter
@NoArgsConstructor
public class RewardedAdGrant {

    @Id
    @Column(name = "transaction_id", length = 128, nullable = false)
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** What was actually paid, which is our figure rather than the one AdMob sends. */
    @Column(name = "coins", nullable = false)
    private int coins;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RewardedAdGrant(String transactionId, String userId, int coins, Instant createdAt) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.coins = coins;
        this.createdAt = createdAt;
    }
}
