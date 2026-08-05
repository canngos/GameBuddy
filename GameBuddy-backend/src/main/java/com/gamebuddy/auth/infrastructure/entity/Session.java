package com.gamebuddy.auth.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A server-side record of an issued token.
 *
 * <p>Two changes from the original. The raw JWT was the primary key, which mapped to
 * {@code varchar(255)} and would fail to insert once the token grew past that; and it
 * meant a database dump handed an attacker every live bearer token verbatim. The token
 * is now identified by its SHA-256 hash, which is fixed-width and useless if leaked.
 */
@Entity
@Table(
        name = "session",
        indexes = {
            @Index(name = "idx_session_token_hash", columnList = "tokenHash", unique = true),
            @Index(name = "idx_session_email", columnList = "email")
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Session {

    @Id
    @GeneratedValue
    private UUID id;

    /** Hex-encoded SHA-256 of the bearer token. Never the token itself. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    private Instant createdDate;
}
