package com.gamebuddy.auth.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the app holds between "your code was correct" and "here is my new password".
 *
 * <p>The reset is two steps because the user is asked for a code and then for a password, and
 * something has to carry the proof between the two screens. That something is deliberately
 * <em>not</em> a session: the app's {@code (auth)} route guard only tolerates a signed-out
 * user, so handing out an access token mid-reset would eject them from the flow — and a
 * token that can do everything is far more than this moment needs. A ticket does one thing.
 *
 * <p>Thirty-two random bytes, handed to the client once and stored only as a SHA-256 hash —
 * the same treatment as {@link Session#getTokenHash()}. A fast digest is the right choice
 * here and the wrong one for the six-digit code next door: this has enough entropy that
 * exhausting it is not a strategy, so there is nothing for bcrypt's cost to protect.
 *
 * <p>Ten minutes, single use. Redeeming one burns every other outstanding ticket for the
 * address, so a reset started twice cannot be finished twice.
 */
@Entity
@Table(
        name = "password_reset_ticket",
        indexes = {
            @Index(name = "idx_password_reset_ticket_hash", columnList = "token_hash", unique = true),
            @Index(name = "idx_password_reset_ticket_email", columnList = "email")
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetTicket {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String email;

    /** SHA-256 hex of the token handed to the client. The token itself is never stored. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Boolean used = Boolean.FALSE;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    public boolean isExpired() {
        return expiresAt == null || Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return !Boolean.TRUE.equals(used) && !isExpired();
    }
}
