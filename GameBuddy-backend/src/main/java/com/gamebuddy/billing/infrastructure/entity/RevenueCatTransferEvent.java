package com.gamebuddy.billing.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A durable claim on one RevenueCat transfer webhook ID. */
@Entity
@Table(name = "revenuecat_transfer_event")
@Getter
@Setter
@NoArgsConstructor
public class RevenueCatTransferEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
