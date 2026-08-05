package com.gamebuddy.common.security;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Issues and verifies the HS256 tokens used across every GameBuddy service.
 *
 * <p>Rewritten for jjwt 0.13 (the 0.11 {@code parserBuilder}/{@code setSigningKey}
 * API used previously was removed). Beyond the API move this tightens three things:
 * the signing key now comes from configuration and is length-checked at startup,
 * the issuer is asserted on parse, and {@link #isTokenValid} additionally honours
 * {@link RevocableUser#getTokensValidFrom()} so a ban or password change
 * immediately invalidates every outstanding token.
 */
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildKey(properties.secret());
    }

    private static SecretKey buildKey(String secret) {
        byte[] material;
        try {
            material = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException _) {
            material = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (material.length < 32) {
            throw new IllegalStateException(
                    "gamebuddy.jwt.secret must decode to at least 32 bytes (256 bits) for HS256; got "
                            + material.length);
        }
        return Keys.hmacShaKeyFor(material);
    }

    /**
     * Mints a token whose subject is the user's login name (their email).
     *
     * <p>The two {@code Date.from} calls are not a java.time oversight (java:S2143):
     * jjwt's builder only accepts {@code java.util.Date}. Every value this class hands
     * back to GameBuddy code is an {@link Instant}.
     */
    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(signingKey)
                .compact();
    }

    /** @return the {@code sub} claim, i.e. the user's email. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /** jjwt still speaks {@code java.util.Date}; that stops at this boundary. */
    public Instant extractExpiration(String token) {
        return toInstant(parse(token).getExpiration());
    }

    public Instant extractIssuedAt(String token) {
        return toInstant(parse(token).getIssuedAt());
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /**
     * Full validation: signature, issuer, expiry, subject match, and revocation.
     *
     * @return {@code true} only if the token may be trusted for this principal
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final Claims claims;
        try {
            claims = parse(token);
        } catch (BusinessException _) {
            return false;
        }
        // Objects.equals, not claims.getSubject().equals: a token with no `sub` would
        // otherwise throw NullPointerException out of a method whose contract is to
        // return false for anything it cannot vouch for.
        if (!Objects.equals(claims.getSubject(), userDetails.getUsername())) {
            return false;
        }
        return !isRevoked(claims, userDetails);
    }

    private boolean isRevoked(Claims claims, UserDetails userDetails) {
        if (!(userDetails instanceof RevocableUser revocable)) {
            return false;
        }
        Instant validFrom = revocable.getTokensValidFrom();
        Instant issuedAt = toInstant(claims.getIssuedAt());
        if (validFrom == null || issuedAt == null) {
            return false;
        }
        // Tokens carry second precision, so compare at that granularity to avoid
        // invalidating a token minted in the same second as the revocation stamp.
        return issuedAt.isBefore(validFrom.truncatedTo(ChronoUnit.SECONDS));
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // Covers expired, malformed, wrong-signature and wrong-issuer tokens. The
            // cause is kept for the logs; GlobalExceptionHandler never puts it on the wire.
            throw new BusinessException(TransactionCode.TOKEN_INVALID, e);
        }
    }
}
