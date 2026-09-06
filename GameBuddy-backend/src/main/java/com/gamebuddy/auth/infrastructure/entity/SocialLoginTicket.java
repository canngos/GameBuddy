package com.gamebuddy.auth.infrastructure.entity;

import com.gamebuddy.common.enums.AuthProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carries a Discord sign-in across the browser round trip.
 *
 * <p>Google needs none of this: the native account sheet hands the app an ID token, and the
 * app posts it in one request. Discord has no such sheet, so the user leaves for the system
 * browser and comes back to a callback that has no session on it — and something has to
 * carry the result across a boundary where nothing is authenticated.
 *
 * <p>Same shape and same reasoning as {@link AccountLinkTicket}: 32 random bytes, stored
 * only as a SHA-256 hash, single use, ten minutes.
 *
 * <p><b>Two of these are minted per sign-in, and that is the point.</b> The first is the
 * OAuth {@code state} and proves the callback belongs to a flow this server started; by the
 * time Discord redirects, it has been through their servers, the browser's history and any
 * referrer that came along. The second is minted at the callback, after the exchange, and
 * carries the verified identity — it is the only one that can be traded for a session, and
 * it has never left this server except in the redirect the app itself receives. So the
 * value that travelled is never the value that grants.
 *
 * <p>Unlike a link ticket there is no {@code userId}: a sign-in is precisely the case where
 * there is not yet an account, and which gamer this becomes is decided at the exchange from
 * the subject below.
 */
@Entity
@Table(
        name = "social_login_ticket",
        indexes = {
            @Index(name = "idx_social_login_ticket_hash", columnList = "token_hash", unique = true),
            @Index(name = "idx_social_login_ticket_expires", columnList = "expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class SocialLoginTicket {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private AuthProvider provider;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** Null on the outbound ticket; the verified provider subject on the inbound one. */
    @Column(name = "subject")
    private String subject;

    @Column(name = "email")
    private String email;

    /**
     * Whether the provider vouched for the address.
     *
     * <p>Carried rather than re-derived, because by the time the app exchanges the ticket
     * the provider is no longer being talked to. A null is treated exactly as a false: the
     * only safe reading of "we did not record an answer" is "it was not verified".
     */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /** The provider's display name, for the username suggestion. Never trusted as identity. */
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Takes the instant rather than reading the clock itself.
     *
     * The service that owns these tickets runs on an injected {@link java.time.Clock}, and
     * an entity calling {@code Instant.now()} quietly opts out of it: the ticket a test
     * mints at the service's fixed clock is already expired by real time, so the test
     * passes in the morning and fails in the afternoon.
     */
    public boolean isExpired(Instant now) {
        return expiresAt == null || now.isAfter(expiresAt);
    }

    /** True only for a ticket that has been through the callback and carries an identity. */
    public boolean isRedeemable(Instant now) {
        return !used && !isExpired(now) && subject != null && !subject.isBlank();
    }
}
