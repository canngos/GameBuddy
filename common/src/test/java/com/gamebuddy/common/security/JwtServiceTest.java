package com.gamebuddy.common.security;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * These tokens are the only thing standing between an anonymous request and every
 * user's account across all five services, so the negative cases matter more than the
 * happy path.
 */
class JwtServiceTest {

    private static final String SECRET = "a-test-signing-key-that-is-long-enough-for-hs256";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofDays(7), null, "gamebuddy"));

    private static UserDetails user(String email) {
        return User.withUsername(email).password("x").roles("USER").build();
    }

    /** A principal that can revoke its outstanding tokens, like the real Gamer entity. */
    private record Revocable(String email, Instant validFrom) implements RevocableUser {
        @Override
        public Instant getTokensValidFrom() {
            return validFrom;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        @Override
        public String getPassword() {
            return "x";
        }

        @Override
        public String getUsername() {
            return email;
        }
    }

    @Test
    void testGenerateToken_whenCalled_ProducesTokenThisServiceAccepts() {
        UserDetails principal = user("a@example.com");

        String token = jwtService.generateToken(principal);

        assertTrue(jwtService.isTokenValid(token, principal));
        assertEquals("a@example.com", jwtService.extractUsername(token));
        assertNotNull(jwtService.extractIssuedAt(token));
        assertTrue(jwtService.extractExpiration(token).isAfter(Instant.now()));
    }

    @Test
    @DisplayName("a secret shorter than 256 bits is refused at construction, not at first request")
    void testConstructor_whenSecretTooShort_Throws() {
        JwtProperties tooShort = new JwtProperties("too-short", Duration.ofDays(1), null, "gamebuddy");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtService(tooShort));
        assertTrue(ex.getMessage().contains("32 bytes"));
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void testIsTokenValid_whenSignedWithForeignKey_ReturnsFalse() {
        SecretKey attackerKey =
                Keys.hmacShaKeyFor("an-entirely-different-key-of-sufficient-length".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("a@example.com")
                .issuer("gamebuddy")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(Duration.ofDays(1))))
                .signWith(attackerKey)
                .compact();

        assertFalse(jwtService.isTokenValid(forged, user("a@example.com")));
    }

    @Test
    @DisplayName("an unsigned `alg: none` token is rejected")
    void testIsTokenValid_whenUnsigned_ReturnsFalse() {
        String unsigned = Jwts.builder()
                .subject("a@example.com")
                .issuer("gamebuddy")
                .expiration(Date.from(Instant.now().plus(Duration.ofDays(1))))
                .compact();

        assertFalse(jwtService.isTokenValid(unsigned, user("a@example.com")));
    }

    @Test
    void testIsTokenValid_whenExpired_ReturnsFalse() {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-30), null, "gamebuddy"));
        String expired = shortLived.generateToken(user("a@example.com"));

        assertFalse(jwtService.isTokenValid(expired, user("a@example.com")));
    }

    @Test
    @DisplayName("a token minted for one issuer is not accepted by another")
    void testIsTokenValid_whenIssuerDiffers_ReturnsFalse() {
        JwtService other = new JwtService(new JwtProperties(SECRET, Duration.ofDays(1), null, "someone-else"));
        String token = other.generateToken(user("a@example.com"));

        assertFalse(jwtService.isTokenValid(token, user("a@example.com")));
    }

    @Test
    @DisplayName("a valid token for one account cannot be replayed against another")
    void testIsTokenValid_whenSubjectIsADifferentUser_ReturnsFalse() {
        String token = jwtService.generateToken(user("a@example.com"));

        assertFalse(jwtService.isTokenValid(token, user("b@example.com")));
    }

    @Test
    void testIsTokenValid_whenGarbage_ReturnsFalseRatherThanThrowing() {
        assertFalse(jwtService.isTokenValid("not.a.token", user("a@example.com")));
        assertFalse(jwtService.isTokenValid("", user("a@example.com")));
    }

    @Test
    @DisplayName("revoking after issue invalidates the token — the ban bypass from the audit")
    void testIsTokenValid_whenIssuedBeforeRevocation_ReturnsFalse() {
        Revocable principal = new Revocable("a@example.com", null);
        String token = jwtService.generateToken(principal);

        assertTrue(jwtService.isTokenValid(token, principal));

        Revocable banned = new Revocable("a@example.com", Instant.now().plus(Duration.ofMinutes(1)));
        assertFalse(jwtService.isTokenValid(token, banned));
    }

    @Test
    @DisplayName("a token issued after the revocation stamp still works")
    void testIsTokenValid_whenIssuedAfterRevocation_ReturnsTrue() {
        Revocable principal = new Revocable("a@example.com", Instant.now().minus(1, ChronoUnit.HOURS));

        String token = jwtService.generateToken(principal);

        assertTrue(jwtService.isTokenValid(token, principal));
    }

    @Test
    void testExtractUsername_whenTokenIsInvalid_ThrowsBusinessException() {
        assertThrows(BusinessException.class, () -> jwtService.extractUsername("not.a.token"));
    }

    @Test
    @DisplayName("a base64-encoded secret and its decoded bytes are the same key")
    void testConstructor_whenSecretIsBase64_DecodesIt() {
        String base64 = java.util.Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        JwtService service = new JwtService(new JwtProperties(base64, Duration.ofDays(1), null, "gamebuddy"));

        String token = service.generateToken(user("a@example.com"));
        assertTrue(service.isTokenValid(token, user("a@example.com")));
    }

    @Nested
    @DisplayName("sliding sessions")
    class SlidingSessions {

        @Test
        @DisplayName("a fresh token's session starts now")
        void testGenerateToken_whenNewSession_StampsSessionStart() {
            String token = jwtService.generateToken(user("a@example.com"));

            Instant start = jwtService.extractSessionStart(token);
            assertTrue(Duration.between(start, Instant.now()).abs().toSeconds() <= 5);
        }

        @Test
        @DisplayName("refreshing carries the original start forward, so the session keeps ageing")
        void testGenerateToken_whenRefreshed_KeepsOriginalSessionStart() {
            Instant began = Instant.now().minus(Duration.ofDays(20));

            String refreshed = jwtService.generateToken(user("a@example.com"), began);

            // The token is new — a later expiry is the whole point — but the session is
            // still twenty days old. Resetting this is what would let a stolen token be
            // renewed forever.
            assertEquals(began.getEpochSecond(), jwtService.extractSessionStart(refreshed).getEpochSecond());
            assertTrue(jwtService.extractExpiration(refreshed).isAfter(Instant.now().plus(Duration.ofDays(6))));
        }

        @Test
        @DisplayName("a session inside the ceiling may be extended; one past it may not")
        void testWithinMaxSessionAge_BoundsTheSession() {
            Instant now = Instant.now();
            // The default ceiling is 30 days — see JwtProperties.
            assertTrue(jwtService.withinMaxSessionAge(now.minus(Duration.ofDays(29)), now));
            assertFalse(jwtService.withinMaxSessionAge(now.minus(Duration.ofDays(31)), now));
        }

        @Test
        @DisplayName("a token minted before the claim existed ages from when it was issued")
        void testExtractSessionStart_whenClaimMissing_FallsBackToIssuedAt() {
            // Exactly the shape of a token already in somebody's keychain at deploy time:
            // signed by us, valid, with no session-start claim on it.
            String legacy = Jwts.builder()
                    .subject("a@example.com")
                    .issuer("gamebuddy")
                    .issuedAt(Date.from(Instant.now().minus(Duration.ofDays(2))))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();

            Instant start = jwtService.extractSessionStart(legacy);

            assertEquals(jwtService.extractIssuedAt(legacy), start);
            // And it is still refreshable, so the deploy logs nobody out.
            assertTrue(jwtService.withinMaxSessionAge(start, Instant.now()));
        }
    }
}
