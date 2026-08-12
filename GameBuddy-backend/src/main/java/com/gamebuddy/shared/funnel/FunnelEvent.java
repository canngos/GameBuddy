package com.gamebuddy.shared.funnel;

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

/** One funnel step, reported by the client. Who, what, when — and nothing else. */
@Entity
@Table(name = "funnel_event")
@Getter
@Setter
@NoArgsConstructor
public class FunnelEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private FunnelStep kind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FunnelEvent(String userId, FunnelStep kind, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind;
        this.createdAt = createdAt;
    }
}
