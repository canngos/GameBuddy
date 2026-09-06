package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.infrastructure.entity.Session;
import com.gamebuddy.auth.infrastructure.repository.SessionRepository;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a session row is allowed to contain, and how many of them there may be.
 *
 * <p>Lifted out of {@code DefaultAuthServiceTest} along with the code, when social sign-in
 * needed the same behaviour. The two properties here are the ones that would be quietly lost
 * if somebody wrote a second copy of this: the row stores a hash, and there is one per
 * account.
 */
@DisplayName("SessionIssuer")
class SessionIssuerTest {

    private static final String TOKEN = "a.jwt.token";
    private static final String EMAIL = "me@example.com";

    private JwtService jwtService;
    private SessionRepository sessionRepository;
    private SessionIssuer issuer;
    private Gamer gamer;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        sessionRepository = mock(SessionRepository.class);
        issuer = new SessionIssuer(jwtService, sessionRepository);

        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setEmail(EMAIL);
        gamer.setRole(Role.USER);

        when(jwtService.generateToken(any(), any())).thenReturn(TOKEN);
        when(jwtService.extractExpiration(anyString())).thenReturn(Instant.now().plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("the row stores a hash, never the bearer token itself")
    void storesAHashNotTheToken() {
        assertEquals(TOKEN, issuer.issue(gamer));

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session saved = captor.getValue();

        assertNotEquals(TOKEN, saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length(), "SHA-256 hex is 64 chars");
        assertEquals(EMAIL, saved.getEmail());
    }

    @Test
    @DisplayName("one row per account: the previous session is dropped first")
    void replacesTheOldRow() {
        issuer.issue(gamer);

        // Without this a gamer who signs in weekly accumulates a row a week, and none of
        // them is ever read again.
        verify(sessionRepository).deleteAllByEmail(EMAIL);
    }

    @Test
    @DisplayName("the session start passed through is the one the token is minted with")
    void sessionStartIsHandedToTheToken() {
        Instant began = Instant.parse("2026-09-01T10:00:00Z");

        issuer.issue(gamer, began);

        // The 30-day ceiling rests entirely on this: a refresh passes the original start,
        // so extending a session never resets its age.
        verify(jwtService).generateToken(gamer, began);
    }
}
