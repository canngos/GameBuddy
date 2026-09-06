package com.gamebuddy.config;

import com.gamebuddy.auth.config.SocialAuthProperties;
import com.gamebuddy.auth.infrastructure.client.GoogleIdTokenVerifier;
import com.gamebuddy.auth.infrastructure.client.NimbusGoogleIdTokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Google ID-token verifier, built only where there is a client id to check against.
 *
 * <p><b>Conditional on the property</b>, not defaulted. {@code NimbusJwtDecoder} would happily
 * build with a null audience and then refuse every token that arrived, which is a
 * misconfiguration that looks exactly like an attack in the logs. Without the id there is no
 * bean, {@code DefaultSocialAuthService} sees an empty {@code Optional}, and Google is simply
 * not offered — the same shape as an unconfigured Discord application.
 */
@Slf4j
@Configuration
public class SocialAuthClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "gamebuddy.social.google", name = "web-client-id")
    public GoogleIdTokenVerifier googleIdTokenVerifier(SocialAuthProperties properties) {
        log.info("Google sign-in is configured");
        return new NimbusGoogleIdTokenVerifier(properties);
    }
}
