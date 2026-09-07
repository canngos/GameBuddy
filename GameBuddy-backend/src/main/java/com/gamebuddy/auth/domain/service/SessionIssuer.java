package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.infrastructure.entity.Session;
import com.gamebuddy.auth.infrastructure.repository.SessionRepository;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.common.security.TokenHashing;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mints the session token, and records it against the account.
 *
 * <p>Lifted out of {@code DefaultAuthService} when social sign-in arrived and needed exactly
 * this and nothing else from it. Two copies of "issue a token and write the session row"
 * would be two places to remember the one-row-per-account rule and the session-start
 * distinction — and the first thing to drift would be the ceiling, silently, in the
 * direction of sessions that never expire.
 */
@Component
@RequiredArgsConstructor
public class SessionIssuer {

    private final JwtService jwtService;
    private final SessionRepository sessionRepository;

    /** Starts a new session: the token's clock, and the ceiling's, both begin now. */
    public String issue(Gamer gamer) {
        return issue(gamer, Instant.now());
    }

    /**
     * Records a token against the account, replacing whatever was there.
     *
     * <p>{@code sessionStart} is what separates a login from a refresh: a login passes now, a
     * refresh passes the moment the session originally began, so extending a session never
     * resets its age. The 30-day ceiling depends entirely on that distinction.
     */
    public String issue(Gamer gamer, Instant sessionStart) {
        String token = jwtService.generateToken(gamer, sessionStart);
        // One row per account — a refresh replaces its predecessor rather than piling up a
        // row a week for every gamer who keeps playing.
        sessionRepository.deleteAllByEmail(gamer.getEmail());

        Session session = new Session();
        session.setTokenHash(TokenHashing.sha256Hex(token));
        session.setEmail(gamer.getEmail());
        session.setExpiresAt(jwtService.extractExpiration(token));
        sessionRepository.save(session);
        return token;
    }
}
