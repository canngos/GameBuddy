package com.gamebuddy.common.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain chain;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static UserDetails enabled(String email) {
        return User.withUsername(email).password("x").roles("USER").build();
    }

    @Test
    void testDoFilter_whenTokenIsValid_AuthenticatesTheRequest() throws Exception {
        UserDetails principal = enabled("a@example.com");
        request.addHeader("Authorization", "Bearer good.token.here");
        when(jwtService.extractUsername("good.token.here")).thenReturn("a@example.com");
        when(userDetailsService.loadUserByUsername("a@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid("good.token.here", principal)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(
                principal,
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilter_whenNoAuthorizationHeader_LeavesRequestAnonymous() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("a header that is not a Bearer token is ignored rather than parsed")
    void testDoFilter_whenHeaderIsNotBearer_LeavesRequestAnonymous() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("a malformed token yields an anonymous request, not a 500")
    void testDoFilter_whenTokenIsUnparseable_SwallowsAndContinues() throws Exception {
        request.addHeader("Authorization", "Bearer garbage");
        when(jwtService.extractUsername("garbage")).thenThrow(new BusinessException(TransactionCode.TOKEN_INVALID));

        assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilter_whenSubjectIsUnknown_SwallowsAndContinues() throws Exception {
        request.addHeader("Authorization", "Bearer good.token.here");
        when(jwtService.extractUsername(anyString())).thenReturn("ghost@example.com");
        when(userDetailsService.loadUserByUsername("ghost@example.com"))
                .thenThrow(new UsernameNotFoundException("no such user"));

        assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_whenTokenIsRejected_LeavesRequestAnonymous() throws Exception {
        UserDetails principal = enabled("a@example.com");
        request.addHeader("Authorization", "Bearer stale.token.here");
        when(jwtService.extractUsername(anyString())).thenReturn("a@example.com");
        when(userDetailsService.loadUserByUsername("a@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid(anyString(), eq(principal))).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("a banned user's still-unexpired token does not authenticate — the notif-service bypass")
    void testDoFilter_whenAccountIsLocked_LeavesRequestAnonymous() throws Exception {
        UserDetails locked = User.withUsername("banned@example.com")
                .password("x")
                .roles("USER")
                .accountLocked(true)
                .build();
        request.addHeader("Authorization", "Bearer good.token.here");
        when(jwtService.extractUsername(anyString())).thenReturn("banned@example.com");
        when(userDetailsService.loadUserByUsername("banned@example.com")).thenReturn(locked);
        when(jwtService.isTokenValid(anyString(), eq(locked))).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
