package com.gamebuddy.auth.application.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.auth.domain.service.SocialAuthService;
import com.gamebuddy.auth.interfaces.dto.SocialIdentitiesResponseBody;
import com.gamebuddy.auth.interfaces.dto.SocialSessionResponseBody;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.shared.entity.Gamer;
import java.util.List;
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
 * The shapes this controller has to keep: JSON for the app, a 302 for the browser.
 *
 * <p>Set up like {@link AuthControllerTest} — the chain is loaded so
 * {@code @AuthenticationPrincipal} resolves, but not applied.
 */
@WebMvcTest(SocialAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SocialAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialAuthService socialAuthService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer principal;

    @BeforeEach
    void setUp() {
        principal = new Gamer();
        principal.setUserId(UUID.randomUUID().toString());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a Google token comes back as a session")
    void googleReturnsASession() throws Exception {
        when(socialAuthService.signInWithGoogle(eq("an.id.token"), any()))
                .thenReturn(new SocialSessionResponseBody("a.jwt", "gamer-1", true));

        mockMvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"an.id.token\",\"acceptedTerms\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.accessToken").value("a.jwt"))
                .andExpect(jsonPath("$.body.data.newAccount").value(true));
    }

    @Test
    @DisplayName("an empty token is refused by validation before the service sees it")
    void blankTokenIsRejected() throws Exception {
        mockMvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(socialAuthService, never()).signInWithGoogle(any(), any());
    }

    @Test
    @DisplayName("the terms flag is optional, because most callers already agreed")
    void acceptedTermsIsOptional() throws Exception {
        when(socialAuthService.signInWithGoogle(any(), any()))
                .thenReturn(new SocialSessionResponseBody("a.jwt", "gamer-1", false));

        mockMvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"an.id.token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the Discord callback redirects into the app rather than answering JSON")
    void discordCallbackRedirects() throws Exception {
        when(socialAuthService.completeDiscordLogin("the-code", "the-state"))
                .thenReturn("gamebuddy://social?provider=discord&status=ok&ticket=abc");

        mockMvc.perform(get("/auth/social/discord/callback")
                        .param("code", "the-code")
                        .param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "gamebuddy://social?provider=discord&status=ok&ticket=abc"));
    }

    @Test
    @DisplayName("a cancelled consent screen still comes back to the app")
    void cancelledCallbackStillRedirects() throws Exception {
        when(socialAuthService.completeDiscordLogin(null, null))
                .thenReturn("gamebuddy://social?provider=discord&status=cancelled");

        mockMvc.perform(get("/auth/social/discord/callback")).andExpect(status().isFound());
    }

    @Test
    @DisplayName("the exchange takes a ticket and answers a session")
    void exchangeReturnsASession() throws Exception {
        when(socialAuthService.exchange(eq("a-ticket"), any()))
                .thenReturn(new SocialSessionResponseBody("a.jwt", "gamer-1", false));

        mockMvc.perform(post("/auth/social/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"a-ticket\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.userId").value("gamer-1"));
    }

    @Test
    @DisplayName("identities are read for the caller, never for an id in the request")
    void identitiesUseThePrincipal() throws Exception {
        when(socialAuthService.identities(any())).thenReturn(new SocialIdentitiesResponseBody(List.of(), false));

        mockMvc.perform(get("/auth/social/identities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.hasPassword").value(false));

        verify(socialAuthService).identities(principal);
    }

    @Test
    void unlinkReachesTheService() throws Exception {
        when(socialAuthService.unlink(any(), anyString())).thenReturn(DefaultMessageResponse.of("ok"));

        mockMvc.perform(delete("/auth/social/google")).andExpect(status().isOk());

        verify(socialAuthService).unlink(principal, "google");
    }
}
