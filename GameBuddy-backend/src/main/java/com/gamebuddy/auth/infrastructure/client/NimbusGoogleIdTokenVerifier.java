package com.gamebuddy.auth.infrastructure.client;

import com.gamebuddy.auth.config.SocialAuthProperties;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Checks a Google ID token against Google's published keys.
 *
 * <p>Nimbus through Spring Security rather than Google's own {@code google-api-client}: the
 * decoder is Boot-managed, caches the JWKS itself, and is the same machinery the framework
 * uses for every other JWT — one less HTTP client and one less refresh policy to own.
 *
 * <h2>What is checked, and why each one matters</h2>
 *
 * <ul>
 *   <li><b>Signature</b>, against {@code https://www.googleapis.com/oauth2/v3/certs}. Without
 *       it the token is a JSON document anybody can type.
 *   <li><b>Expiry and not-before</b>, from {@link JwtValidators#createDefault()}.
 *   <li><b>Issuer</b>, which must be Google. Both spellings are accepted because Google
 *       genuinely issues both, with and without the scheme.
 *   <li><b>Audience</b>, which must be <em>our</em> web client id. This is the check people
 *       skip, and skipping it is the whole vulnerability: a valid Google token issued to any
 *       other application would otherwise sign its holder in here as that token's subject.
 * </ul>
 *
 * <p>Every failure becomes one code. The specifics go to the log, where they help us, rather
 * than into a response, where they would help whoever is holding a forged token.
 */
@Slf4j
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    /** Google issues both spellings, and which one arrives is not ours to decide. */
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder decoder;

    public NimbusGoogleIdTokenVerifier(SocialAuthProperties properties) {
        String audience = properties.getGoogle().getWebClientId();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(
                        properties.getGoogle().getJwkSetUri())
                .build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), issuedByGoogle(), addressedToUs(audience)));
        this.decoder = nimbus;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "no id token");
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // Includes a bad signature, an expired token, the wrong audience, and Google's
            // key endpoint being unreachable. The first three are somebody else's problem
            // and the fourth is ours, but the caller's next move is identical.
            log.info("Google id token refused: {}", e.getMessage());
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "token did not verify");
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "no subject");
        }

        // `email_verified` arrives as a boolean, but a claim read off a JSON document is a
        // claim: anything that is not literally true is treated as false.
        Object verified = jwt.getClaim("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));

        return new GoogleIdentity(subject, jwt.getClaimAsString("email"), emailVerified, jwt.getClaimAsString("name"));
    }

    private static OAuth2TokenValidator<Jwt> issuedByGoogle() {
        return jwt -> ISSUERS.contains(
                        jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_issuer"));
    }

    /**
     * The audience check.
     *
     * <p>Compared against the <em>web</em> client id even though the token is minted on an
     * Android device: Google issues ID tokens whose {@code aud} is the server's client id
     * precisely so that a backend can tell tokens meant for it from tokens meant for anyone
     * else. The Android client id appears in {@code azp} and is not what is checked here.
     */
    private static OAuth2TokenValidator<Jwt> addressedToUs(String audience) {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            return aud != null && aud.contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_audience"));
        };
    }
}
