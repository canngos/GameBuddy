package com.gamebuddy.auth.infrastructure.entity;

import com.gamebuddy.common.enums.LinkedProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What ties a browser coming back from Discord to the account that sent it there.
 *
 * <p>The flow leaves the app: the user is handed to the system browser, and the provider
 * hands the browser back to a callback endpoint that has no session and no token — it is
 * necessarily public, in the same way RevenueCat's webhook is. Something in that URL has to
 * say which account is linking, and it must not be the JWT: putting a seven-day credential
 * in a redirect sprays it through browser history, the provider's logs, and any referrer
 * that comes along for the ride.
 *
 * <p>So it is a ticket, with the same shape and the same reasoning as
 * {@link PasswordResetTicket} — 32 random bytes, handed out once, stored only as a SHA-256
 * hash, single use, short lived. It carries no authority beyond "finish this one link for
 * this one gamer on this one provider", which is all the callback needs to be allowed to do.
 *
 * <p>It doubles as OAuth {@code state}. That is not two jobs bolted together: what
 * {@code state} is for is proving the callback belongs to a flow this server started, and a
 * server-bound unguessable single-use token is precisely that. Ten minutes, because a
 * consent screen is answered in seconds and an abandoned attempt should stop being a way in.
 */
@Entity
@Table(
        name = "account_link_ticket",
        indexes = {
            @Index(name = "idx_account_link_ticket_hash", columnList = "token_hash", unique = true),
            @Index(name = "idx_account_link_ticket_user", columnList = "user_id, provider")
        })
@Getter
@Setter
@NoArgsConstructor
public class AccountLinkTicket {

    @Id
    @GeneratedValue
    private UUID id;

    /** Who is linking. The callback learns the account from this and from nothing else. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * Which provider the ticket was minted for.
     *
     * <p>Checked at the callback, so a ticket minted for one provider cannot be presented
     * at another's endpoint. Two providers' callbacks verify entirely different things, and
     * a ticket that worked at either would let the weaker verification stand in for the
     * stronger. Only Discord remains today; the check is what keeps that safe to change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16, nullable = false)
    private LinkedProvider provider;

    /** SHA-256 hex of the token put in the redirect URL. The token itself is never stored. */
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
