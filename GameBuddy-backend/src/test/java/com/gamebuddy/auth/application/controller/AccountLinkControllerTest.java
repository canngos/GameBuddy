package com.gamebuddy.auth.application.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.auth.domain.service.AccountLinkService;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.shared.entity.Gamer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Set up like {@link AuthControllerTest} — the chain is loaded so
 * {@code @AuthenticationPrincipal} resolves, but not applied, and the principal goes
 * straight into the {@code SecurityContextHolder}.
 *
 * <p>What is worth testing at this layer is the shape of the callback, which is unlike every
 * other endpoint here: it answers a browser with a redirect rather than the app with JSON,
 * and it has to keep doing so when the link fails.
 */
@WebMvcTest(AccountLinkController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccountLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountLinkService accountLinkService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer principal;

    @BeforeEach
    void setUp() {
        principal = new Gamer();
        principal.setUserId(UUID.randomUUID().toString());
        principal.setEmail("test@example.com");
        principal.setRole(Role.USER);
        principal.setIsBlocked(false);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the providers list is reachable")
    void providersIsServed() throws Exception {
        when(accountLinkService.availableProviders()).thenReturn(null);

        mockMvc.perform(get("/auth/link/providers")).andExpect(status().isOk());

        verify(accountLinkService).availableProviders();
    }

    @Test
    @DisplayName("starting a link passes the caller through, so the service never guesses the account")
    void startPassesThePrincipal() throws Exception {
        when(accountLinkService.startLink(any(), anyString())).thenReturn(null);

        mockMvc.perform(post("/auth/link/discord/start")).andExpect(status().isOk());

        verify(accountLinkService).startLink(principal, "discord");
    }

    @Test
    @DisplayName("the Discord callback redirects into the app")
    void discordCallbackRedirects() throws Exception {
        when(accountLinkService.completeDiscordLink("the-code", "the-ticket"))
                .thenReturn("gamebuddy://settings/linked?provider=discord&status=ok");

        mockMvc.perform(get("/auth/link/discord/callback")
                        .param("code", "the-code")
                        .param("state", "the-ticket"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "gamebuddy://settings/linked?provider=discord&status=ok"));
    }

    @Test
    @DisplayName("a cancelled consent screen still comes back to the app")
    void discordCallbackWithoutParametersStillRedirects() throws Exception {
        when(accountLinkService.completeDiscordLink(null, null))
                .thenReturn("gamebuddy://settings/linked?provider=discord&status=failed");

        mockMvc.perform(get("/auth/link/discord/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "gamebuddy://settings/linked?provider=discord&status=failed"));
    }

    @Test
    @DisplayName("visibility is a body, not a path segment")
    void visibilityIsSettable() throws Exception {
        when(accountLinkService.setVisibility(any(), anyString(), any())).thenReturn(DefaultMessageResponse.of("ok"));

        mockMvc.perform(put("/auth/link/discord/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an empty visibility is refused by validation before the service sees it")
    void blankVisibilityIsRejected() throws Exception {
        mockMvc.perform(put("/auth/link/discord/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(accountLinkService, never()).setVisibility(any(), anyString(), any());
    }

    @Test
    void unlinkReachesTheService() throws Exception {
        when(accountLinkService.unlink(any(), anyString())).thenReturn(DefaultMessageResponse.of("ok"));

        mockMvc.perform(delete("/auth/link/discord")).andExpect(status().isOk());

        verify(accountLinkService).unlink(principal, "discord");
    }

}
