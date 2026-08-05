package com.gamebuddy.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin rules for browser callers.
 *
 * <p>There were none, which was invisible for a long time because the only client was
 * an Android app: native HTTP has no origin and is not subject to CORS at all. A
 * browser is, and the failure is unhelpfully quiet — the preflight is refused, {@code
 * fetch} rejects with a bare {@code TypeError}, and the app can only report it as "could
 * not reach the server". Identical, from the client's side, to the backend being down.
 *
 * <p>The default is <strong>no allowed origins</strong>, which keeps the previous
 * behaviour: no browser may call this API unless someone says which one. That is the
 * right default for a service whose real clients are mobile, and it means a production
 * deployment cannot accidentally inherit a permissive development setting.
 *
 * <p>Set {@code gamebuddy.cors.allowed-origins} to switch it on — {@code
 * docker-compose.yml} lists the Expo dev server's origins so the web build works
 * locally. Patterns rather than literals, because Metro picks a port and the LAN
 * address varies by machine.
 */
@Configuration
public class CorsConfig {

    /**
     * Origin patterns, not origins: {@code setAllowedOriginPatterns} permits wildcards
     * that {@code setAllowedOrigins} rejects, which is what makes {@code
     * http://localhost:[*]} expressible.
     */
    @Value("${gamebuddy.cors.allowed-origins:}")
    private String[] allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        List<String> patterns = Arrays.stream(allowedOriginPatterns)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();

        // Nothing registered means no CORS mapping at all, so the browser default
        // applies and cross-origin requests are refused. Registering an empty
        // allow-list would be the same outcome by a slower route.
        if (patterns.isEmpty()) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // No credentials: this API authenticates with a bearer header, never a cookie,
        // so allowing credentials would widen the surface for nothing in return.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
