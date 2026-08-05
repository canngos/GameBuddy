package com.gamebuddy.match.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * The WebSocket had no authentication at all, and the chat controller read the sender
 * from the client payload — so anyone able to open a socket could post as any user. These
 * cover the CONNECT frame, which is where that is now stopped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompAuthChannelInterceptorTest {

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private MessageChannel channel;

    private static UserDetails user(String email) {
        return User.withUsername(email).password("x").roles("USER").build();
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void testPreSend_whenTokenIsValid_BindsThePrincipal() {
        UserDetails principal = user("a@example.com");
        when(jwtService.extractUsername("good.token")).thenReturn("a@example.com");
        when(userDetailsService.loadUserByUsername("a@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid("good.token", principal)).thenReturn(true);

        Message<?> result = interceptor.preSend(connect("Bearer good.token"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertNotNull(accessor.getUser());
        assertEquals("a@example.com", accessor.getUser().getName());
    }

    @Test
    @DisplayName("a CONNECT with no Authorization header is refused")
    void testPreSend_whenNoToken_Rejects() {
        Message<byte[]> message = connect(null);

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void testPreSend_whenHeaderIsNotBearer_Rejects() {
        Message<byte[]> message = connect("Basic dXNlcjpwYXNz");

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void testPreSend_whenTokenIsUnparseable_Rejects() {
        when(jwtService.extractUsername(anyString())).thenThrow(new BusinessException(TransactionCode.TOKEN_INVALID));
        Message<byte[]> message = connect("Bearer garbage");

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void testPreSend_whenSubjectUnknown_Rejects() {
        when(jwtService.extractUsername(anyString())).thenReturn("ghost@example.com");
        when(userDetailsService.loadUserByUsername("ghost@example.com"))
                .thenThrow(new UsernameNotFoundException("no such user"));
        Message<byte[]> message = connect("Bearer good.token");

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void testPreSend_whenTokenRevoked_Rejects() {
        UserDetails principal = user("a@example.com");
        when(jwtService.extractUsername(anyString())).thenReturn("a@example.com");
        when(userDetailsService.loadUserByUsername("a@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid(anyString(), eq(principal))).thenReturn(false);
        Message<byte[]> message = connect("Bearer stale.token");

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("a banned gamer cannot open a chat socket")
    void testPreSend_whenAccountLocked_Rejects() {
        UserDetails locked = User.withUsername("banned@example.com")
                .password("x")
                .roles("USER")
                .accountLocked(true)
                .build();
        when(jwtService.extractUsername(anyString())).thenReturn("banned@example.com");
        when(userDetailsService.loadUserByUsername("banned@example.com")).thenReturn(locked);
        when(jwtService.isTokenValid(anyString(), eq(locked))).thenReturn(true);
        Message<byte[]> message = connect("Bearer good.token");

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("frames other than CONNECT pass through: the session principal already applies")
    void testPreSend_whenNotConnect_PassesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> send = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertSame(send, interceptor.preSend(send, channel));
        verifyNoInteractions(jwtService);
    }
}
