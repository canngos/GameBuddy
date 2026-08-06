package com.gamebuddy.common.security;

import com.gamebuddy.common.observability.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Single JWT filter shared by all five services, replacing five near-identical copies.
 *
 * <p>Fixes carried over from the audit:
 * <ul>
 *   <li>a malformed token no longer escapes as a 500 — it simply leaves the request
 *       unauthenticated, which the entry point then turns into a clean 401;
 *   <li>{@code isAccountNonLocked()} is checked here for every service (notif-service
 *       previously omitted it, so banned users kept full access);
 *   <li>token revocation via {@link RevocableUser} is honoured.
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = header.substring(BEARER_PREFIX.length()).trim();
        try {
            String email = jwtService.extractUsername(jwt);
            if (email != null) {
                UserDetails user = userDetailsService.loadUserByUsername(email);
                if (jwtService.isTokenValid(jwt, user) && user.isAccountNonLocked() && user.isEnabled()) {
                    var authToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    // From here on, every line this request writes says who it was for.
                    // RequestLoggingFilter wraps this one, so it also clears it.
                    if (user instanceof RevocableUser revocable) {
                        LogContext.setUserId(revocable.getLogIdentifier());
                    }
                }
            }
        } catch (RuntimeException e) {
            // Deliberately swallowed. Covers UsernameNotFoundException plus any
            // parse/verify failure: an unparseable, expired or unknown-subject token
            // must yield 401 from the entry point, never a 500 from here.
            log.debug("Rejecting bearer token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
