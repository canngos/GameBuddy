package com.gamebuddy.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates service-to-service calls with a shared key.
 *
 * <p>Some endpoints are only ever called by another GameBuddy service, never by a user:
 * {@code /notif/token} and {@code /notif/topic} are invoked by application-service and
 * match-service on a user's behalf. They have no user principal to authenticate, and they
 * were simply left {@code permitAll} — so anyone who could reach the service could push a
 * notification to any device, or broadcast to every user at once.
 *
 * <p>A caller presenting the key is granted {@code ROLE_INTERNAL}, which the filter chain
 * can then require. This is a second line of defence behind the network boundary — with
 * the services on ClusterIP behind one ingress, these paths are not routable from outside
 * the cluster in the first place.
 */
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    public static final String ROLE = "ROLE_INTERNAL";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final byte[] expectedKey;
    private final List<String> protectedPatterns;

    public InternalApiKeyFilter(String apiKey, List<String> protectedPatterns) {
        if (apiKey == null || apiKey.length() < 32) {
            throw new IllegalStateException(
                    "gamebuddy.internal.api-key must be set and at least 32 characters; generate with "
                            + "openssl rand -base64 32");
        }
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.protectedPatterns = List.copyOf(protectedPatterns);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(HEADER);
        if (presented == null || !matches(presented)) {
            log.warn("Rejected internal call to {} without a valid {}", request.getRequestURI(), HEADER);
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority(ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        return protectedPatterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    /** Constant-time, so a wrong key cannot be recovered by timing the comparison. */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }
}
