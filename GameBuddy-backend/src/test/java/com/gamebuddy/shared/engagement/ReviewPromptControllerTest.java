package com.gamebuddy.shared.engagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.shared.entity.Gamer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The endpoint answers one boolean, and keeps answering it when things go wrong.
 *
 * <p>Set up like the other controller slices: the chain is loaded so
 * {@code @AuthenticationPrincipal} resolves, but not applied, and the principal goes
 * straight into the {@code SecurityContextHolder}.
 */
@WebMvcTest(ReviewPromptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReviewPromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewPromptService reviewPromptService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer principal;

    @BeforeEach
    void setUp() {
        principal = new Gamer();
        principal.setUserId("gamer-1");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a granted claim comes back as due")
    void grantedClaim() throws Exception {
        when(reviewPromptService.claim(any())).thenReturn(true);

        mockMvc.perform(post("/engagement/review-prompt/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.due").value(true));

        verify(reviewPromptService).claim(principal);
    }

    @Test
    @DisplayName("a refused claim is a 200 saying no, not an error")
    void refusedClaim() throws Exception {
        when(reviewPromptService.claim(any())).thenReturn(false);

        mockMvc.perform(post("/engagement/review-prompt/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.due").value(false));
    }

    @Test
    @DisplayName("a service that blows up still answers, because the caller is dismissing a match")
    void failureIsSwallowed() throws Exception {
        when(reviewPromptService.claim(any())).thenThrow(new IllegalStateException("database gone"));

        mockMvc.perform(post("/engagement/review-prompt/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.due").value(false));
    }
}
