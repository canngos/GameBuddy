package com.gamebuddy.common.security;

import java.time.Instant;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A principal whose already-issued tokens can be invalidated en masse.
 *
 * <p>Previously nothing revoked a JWT: banning a user or changing their password
 * deleted a row from {@code session}, but no service ever consulted that table, so
 * the stolen or stale token kept working for the rest of its 30-day life.
 *
 * <p>Every service now stamps {@code tokensValidFrom} on the gamer record and the
 * JWT filter rejects any token issued before it. This is stateless — it needs no
 * cross-service call and no shared cache — because all services already load the
 * gamer from the shared database on each request.
 */
public interface RevocableUser extends UserDetails {

    /**
     * Tokens issued strictly before this instant are rejected. {@code null} means
     * "nothing has been revoked", so all otherwise-valid tokens are accepted.
     */
    Instant getTokensValidFrom();
}
