package com.gamebuddy.config;

import com.gamebuddy.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * One filter chain for the whole application.
 *
 * <p>Merged from five per-service configurations. That merge is worth doing carefully:
 * each service previously declared its own rules, and a path only reachable through one
 * service was only ever protected by that service's chain. In one process every path is
 * matched against this single list, so a rule that was implicit — "nobody can reach
 * {@code /notif/token} except from inside the cluster" — has to become explicit.
 *
 * <p><strong>Everything not listed requires authentication.</strong> The public set is
 * deliberately short: registration and login, because you cannot hold a token before you
 * have an account; the two harmless actuator endpoints; the API docs, which are disabled
 * outside development anyway; and the WebSocket handshake, which authenticates from the
 * STOMP CONNECT frame instead — see {@code StompAuthChannelInterceptor}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Public paths. Anything absent from this list needs a valid token.
     *
     * <p>{@code /actuator/**} is deliberately not here. It was wide open in the original,
     * publishing {@code /actuator/env} and {@code /actuator/heapdump} — configuration and
     * a memory image, to anyone who asked.
     */
    private static final String[] PUBLIC_PATHS = {
        "/auth/login",
        "/auth/register",
        "/auth/sendCode",
        "/auth/verify",
        "/actuator/health/**",
        "/actuator/info",
        "/api-docs/**",
        "/api-docs.yaml",
        "/swagger-ui/**",
        "/swagger-ui.html",
        // The handshake only; STOMP CONNECT carries the token and is checked by the
        // channel interceptor. Requiring a header here would break browser WebSocket
        // clients, which cannot set one on the upgrade request.
        "/ws/**",
        // Approved images, in development only — MediaController does not exist when R2
        // is configured, and in production R2 serves these bytes without this application
        // being involved at all. Public because an <Image> fetches a URL with no
        // Authorization header, exactly as it would against R2's public bucket. Only the
        // MEDIA bucket is reachable through it; nothing awaiting review is.
        "/media/**",
        // RevenueCat's webhook. Public to Spring Security because the caller is a server
        // with no account and no JWT, but *not* unauthenticated: the controller checks a
        // shared secret sent in the Authorization header and refuses without it. This is
        // the only path that can grant a paid entitlement, so that check is the whole
        // security boundary for billing — see RevenueCatWebhookController.
        "/billing/revenuecat/webhook",
        // AdMob's rewarded-ad callback. Public for the same reason as the line above —
        // Google's servers call it and have no account here — but authenticated by the
        // ECDSA signature AdMob puts on the query string, checked against Google's
        // published verifier keys. This is the only path that can mint coins without a
        // session, so that signature check is the whole security boundary for the ad
        // economy; see RewardedAdController and RewardedAdVerifier.
        "/ads/reward"
    };

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                // Must be inside the chain, not merely a bean: a CORS preflight is an
                // unauthenticated OPTIONS request with no token, so without this it is
                // rejected by the rules below before any CORS handling runs. See
                // CorsConfig — the default allows no origins at all.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // java:S4502 reviewed and accepted. CSRF requires an ambient credential
                // the browser attaches automatically. This API has none: no session
                // (STATELESS below), no auth cookie — the JWT arrives in an Authorization
                // header that only first-party code can set. Re-open this decision if a
                // cookie-based flow is ever added.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        // Enforced at the chain as well as per-method inside the admin
                        // services: two independent checks, so neither is the only thing
                        // standing between a user and someone else's account.
                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 401 rather than a redirect to a login page that does not exist.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
