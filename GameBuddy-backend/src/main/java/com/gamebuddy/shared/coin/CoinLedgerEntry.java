package com.gamebuddy.shared.coin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One movement of the currency.
 *
 * <p>The balance on {@code gamer} remains the source of truth for whether somebody can
 * afford something — a running SUM on the purchase path would be a poor trade — so this is
 * a record of what happened rather than a derivation of what is. The two can drift; the
 * reconciliation query in the analytics repository is what reports whether they have.
 */
@Entity
@Table(name = "coin_ledger")
@Getter
@Setter
@NoArgsConstructor
public class CoinLedgerEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Signed: positive earns, negative spends. Never zero. */
    @Column(name = "delta", nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private CoinReason reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CoinLedgerEntry(String userId, int delta, CoinReason reason, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.delta = delta;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
