package com.gamebuddy.common.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalApiKeyFilterTest {

    private static final String KEY = "a-shared-internal-key-of-sufficient-length";

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter(KEY, List.of("/notif/token", "/notif/topic"));

    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String uri, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        if (key != null) {
            request.addHeader(InternalApiKeyFilter.HEADER, key);
        }
        return request;
    }

    @Test
    void testFilter_whenKeyIsCorrect_GrantsTheInternalRole() throws Exception {
        filter.doFilter(request("/notif/token", KEY), response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(InternalApiKeyFilter.ROLE)));
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("a protected path with no key is refused — this is what was permitAll")
    void testFilter_whenKeyIsMissing_Returns401() throws Exception {
        filter.doFilter(request("/notif/token", null), response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    @Test
    void testFilter_whenKeyIsWrong_Returns401() throws Exception {
        filter.doFilter(request("/notif/topic", "not-the-key"), response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("unprotected paths are untouched, so the user token still governs them")
    void testFilter_whenPathIsNotProtected_PassesThrough() throws Exception {
        filter.doFilter(request("/notif/showall", null), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("a key that is missing or too short fails at construction, not at first request")
    void testConstructor_whenKeyIsWeak_Throws() {
        List<String> paths = List.of("/notif/token");

        assertThrows(IllegalStateException.class, () -> new InternalApiKeyFilter(null, paths));
        assertThrows(IllegalStateException.class, () -> new InternalApiKeyFilter("short", paths));
    }
}
