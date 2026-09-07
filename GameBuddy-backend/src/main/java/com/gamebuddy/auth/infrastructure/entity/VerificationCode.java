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
 *
 * <p><b>The code is stored as a bcrypt hash, never in the clear.</b> A fast digest would
 * buy nothing: six digits is a million values, so anyone holding this table could exhaust
 * the space in seconds. Bcrypt's cost is the control. The consequence for callers is that a
 * row cannot be looked up <em>by</em> code — find the live row for the address and compare.
 *
 * <p><b>And it is scoped to one purpose.</b> Without that, a code mailed for a password
 * reset was redeemable at {@code /auth/verify}, which signs the account in — a passwordless
 * login nobody designed.
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

    @Column(name = "code_hash", nullable = false, length = 60)
    private String codeHash;

    /** What this code may be redeemed for. Never null; see the class comment. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CodePurpose purpose = CodePurpose.REGISTRATION;

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
