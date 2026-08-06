package com.gamebuddy.match.config;

import com.gamebuddy.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame and binds the result to the session.
 *
 * <p>This is the fix for the most serious defect in the system. The WebSocket endpoint
 * was {@code permitAll} and nothing anywhere authenticated a STOMP frame, while
 * {@code ChatController#processMessage} read the sender straight out of the client's JSON
 * payload. Anyone who could open a socket — no account required — could therefore post a
 * message as any user to any user, and it would be stored and delivered as genuine. The
 * HTTP filter chain could not catch this: it only sees the initial handshake, not the
 * frames that follow.
 *
 * <p>The client must send the token as a {@code Authorization: Bearer <jwt>} native header
 * on CONNECT. Once set here, Spring propagates the principal to every subsequent frame on
 * the session, so {@code processMessage} can trust {@code Principal#getName}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * {@inheritDoc}
     *
     * <p>The {@code @Nullable} return mirrors {@code ChannelInterceptor.preSend}, which
     * may return null to drop a message. This implementation never does — a frame it will
     * not accept is refused by throwing — but an override has to state the same contract
     * as the interface it implements.
     *
     * <p>java:S2638 reviewed and accepted: Sonar reports the same incompatibility whether
     * the annotation is present or absent, so it cannot be satisfied by changing the
     * declaration. The package is {@code @NullMarked} and this matches the interface.
     */
    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("A bearer token is required to open a chat session");
        }

        String jwt = header.substring(BEARER_PREFIX.length()).trim();
        UserDetails user;
        try {
            String email = jwtService.extractUsername(jwt);
            user = userDetailsService.loadUserByUsername(email);
        } catch (RuntimeException e) {
            log.debug("Rejecting STOMP CONNECT: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!jwtService.isTokenValid(jwt, user) || !user.isAccountNonLocked() || !user.isEnabled()) {
            // WARN rather than DEBUG, unlike the parse failure above: the token was
            // readable and the account exists, so this is a valid-looking credential being
            // refused — an expired session, or a banned account still trying to connect.
            log.warn("Refusing a chat session for a token that no longer authorises one");
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Principal.getName() is the gamer's email, so user destinations are keyed by
        // email on both ends: a client subscribing to /user/queue/messages resolves
        // against its own principal, and ChatController sends to the recipient's.
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return message;
    }
}
