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
 * @param issuer value written to the {@code iss} claim and required on parse
 */
@Validated
@ConfigurationProperties(prefix = "gamebuddy.jwt")
public record JwtProperties(@NotBlank String secret, Duration expiration, String issuer) {

    public JwtProperties {
        // Previously 30 days. Shortened to 7; override via gamebuddy.jwt.expiration.
        if (expiration == null) {
            expiration = Duration.ofDays(7);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "gamebuddy";
        }
    }
}
