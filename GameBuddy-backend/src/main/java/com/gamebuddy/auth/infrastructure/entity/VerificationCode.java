package com.gamebuddy.auth.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A one-time e-mail verification / password-reset code.
 *
 * <p>The original entity stored only {@code code}, {@code email} and {@code isValid}
 * with no expiry and no attempt counter, and the "invalidate previous codes" step was
 * never persisted. Every code ever issued therefore stayed valid forever, which made
 * the 6-digit space brute-forceable. Expiry and attempt limits now live here so they
 * are enforced in the database rather than in memory.
 */
@Entity
@Table(
        name = "verification_code",
        indexes = {@Index(name = "idx_verification_code_email", columnList = "email")})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VerificationCode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private Integer code;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Boolean isValid = Boolean.TRUE;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Failed match attempts against this code; the code burns once the cap is hit. */
    @Column(nullable = false)
    private Integer attempts = 0;

    public boolean isExpired() {
        return expiresAt == null || Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return Boolean.TRUE.equals(isValid) && !isExpired();
    }
}
