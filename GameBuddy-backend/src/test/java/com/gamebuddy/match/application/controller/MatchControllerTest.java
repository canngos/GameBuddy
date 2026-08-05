package com.gamebuddy.match.application.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.match.domain.service.MatchService;
import com.gamebuddy.match.interfaces.dto.AcceptResponseBody;
import com.gamebuddy.match.interfaces.dto.GamerDto;
import com.gamebuddy.match.interfaces.dto.RecommendationResponseBody;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
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
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(MatchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer principal;

    @BeforeEach
    void setUp() {
        principal = new Gamer();
        principal.setUserId(UUID.randomUUID().toString());
        principal.setEmail("me@example.com");
        principal.setGamerUsername("me");

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static RecommendationResponse recommendations(String username) {
        RecommendationResponse response = new RecommendationResponse();
        RecommendationResponseBody body = new RecommendationResponseBody();
        GamerDto dto = new GamerDto();
        dto.setUserId(UUID.randomUUID().toString());
        dto.setGamerUsername(username);
        body.setRecommendedGamers(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Test
    void testGetRecommendations_whenCalled_ReturnsRecommendedGamers() throws Exception {
        when(matchService.getRecommendations(any())).thenReturn(recommendations("buddy"));

        mockMvc.perform(get("/match/get/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.recommendedGamers[0].gamerUsername")
                        .value("buddy"));

        verify(matchService).getRecommendations(argThat(g -> g.getUserId().equals(principal.getUserId())));
    }

    @Test
    void testGetSelectedGameRecommendations_whenCalled_PassesGameId() throws Exception {
        when(matchService.getSelectedGameRecommendations(any(), eq("g1"))).thenReturn(recommendations("buddy"));

        mockMvc.perform(get("/match/get/selected/game/g1")).andExpect(status().isOk());

        verify(matchService).getSelectedGameRecommendations(any(), eq("g1"));
    }

    @Test
    void testAcceptMatch_whenValidBody_ReturnsSuccess() throws Exception {
        GamerRequest request = new GamerRequest();
        request.setUserId(UUID.randomUUID().toString());
        when(matchService.acceptGamer(any(), any())).thenReturn(acceptResponse(false));

        mockMvc.perform(post("/match/accept")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(matchService).acceptGamer(argThat(g -> g.getUserId().equals(principal.getUserId())), any());
    }

    @Test
    @DisplayName("an empty userId is rejected before the service is reached")
    void testAcceptMatch_whenUserIdBlank_ShouldReturn400() throws Exception {
        GamerRequest request = new GamerRequest();
        request.setUserId("");

        mockMvc.perform(post("/match/accept")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(matchService, never()).acceptGamer(any(), any());
    }

    @Test
    void testDeclineMatch_whenValidBody_ReturnsSuccess() throws Exception {
        GamerRequest request = new GamerRequest();
        request.setUserId(UUID.randomUUID().toString());
        when(matchService.declineGamer(any(), any())).thenReturn(DefaultMessageResponse.of("Gamer declined"));

        mockMvc.perform(post("/match/decline")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /** Builds the accept payload the controller now returns. */
    private static AcceptResponse acceptResponse(boolean matched) {
        AcceptResponse response = new AcceptResponse();
        response.setBody(new BaseBody<>(new AcceptResponseBody(matched, matched ? "It's a match!" : "Gamer accepted")));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
