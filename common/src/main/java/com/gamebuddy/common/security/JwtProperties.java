package com.gamebuddy.common.security;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings, bound from {@code gamebuddy.jwt.*}.
 *
 * <p>The secret is deliberately mandatory with no default. The previous library
 * shipped a compiled-in signing key, which meant anyone with the published artifact
 * could mint a valid token for any account.
 *
 * @param secret base64 or raw signing key; must decode to at least 32 bytes for HS256
 * @param expiration how long a freshly issued token stays valid
 * @param maxSessionAge how long a session may be extended for before the password is
 *     required again, counted from the login that started it
 * @param issuer value written to the {@code iss} claim and required on parse
 */
@Validated
@ConfigurationProperties(prefix = "gamebuddy.jwt")
public record JwtProperties(
        @NotBlank String secret, Duration expiration, Duration maxSessionAge, String issuer) {

    public JwtProperties {
        // Previously 30 days. Shortened to 7; override via gamebuddy.jwt.expiration.
        if (expiration == null) {
            expiration = Duration.ofDays(7);
        }
        /*
         * The ceiling on sliding sessions.
         *
         * A refreshable token that never stops being refreshable is a stolen token that
         * never stops working, which would give back everything the 30-day-to-7-day
         * shortening above was buying. So a session slides while it is being used and
         * still dies for good after this long, counted from the login that began it.
         *
         * Thirty days is the trade: somebody who plays weekly types their password
         * about once a month, and a phone lifted from a table is worth at most a month.
         */
        if (maxSessionAge == null) {
            maxSessionAge = Duration.ofDays(30);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "gamebuddy";
        }
    }
}
