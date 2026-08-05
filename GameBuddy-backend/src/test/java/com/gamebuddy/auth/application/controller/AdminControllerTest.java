package com.gamebuddy.auth.application.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.auth.domain.service.AdminService;
import com.gamebuddy.auth.interfaces.dto.*;
import com.gamebuddy.auth.interfaces.request.GameRequest;
import com.gamebuddy.auth.interfaces.request.KeywordRequest;
import com.gamebuddy.auth.interfaces.response.GamerResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer admin;

    @BeforeEach
    void setUp() {
        admin = new Gamer();
        admin.setUserId(UUID.randomUUID().toString());
        admin.setEmail("admin@example.com");
        admin.setGamerUsername("admin");
        admin.setRole(Role.ADMIN);
        admin.setIsBlocked(false);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static DefaultMessageResponse ok(String message) {
        return DefaultMessageResponse.of(message);
    }

    @Test
    void testGetBlockedUsers_whenCalledByAdmin_shouldReturnBlockedUsers() throws Exception {
        GamerDto dto = new GamerDto();
        dto.setUserId(UUID.randomUUID().toString());
        dto.setUsername("victim");
        dto.setEmail("victim@example.com");
        dto.setCreatedDate(Instant.now());

        GamerResponse response = new GamerResponse();
        GamerResponseBody body = new GamerResponseBody();
        body.setBlockedUsers(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(adminService.getBlockedUsers(any())).thenReturn(response);

        mockMvc.perform(get("/admin/get/blocked/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.blockedUsers[0].username").value("victim"));
    }

    @Test
    void testBanUser_whenValidUserIdProvided_shouldReturnSuccessMessage() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(adminService.banUser(any(), eq(userId))).thenReturn(ok("User victim blocked successfully"));

        mockMvc.perform(post("/admin/ban/user/" + userId)).andExpect(status().isOk());
        verify(adminService).banUser(argThat(g -> g.getUserId().equals(admin.getUserId())), eq(userId));
    }

    @Test
    void testUnbanUser_whenValidUserIdProvided_shouldReturnSuccessMessage() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(adminService.unbanUser(any(), eq(userId))).thenReturn(ok("User victim unblocked successfully"));

        mockMvc.perform(post("/admin/unban/user/" + userId)).andExpect(status().isOk());
    }

    @Test
    void testAddGame_whenValidGameInfoProvided_shouldReturnSuccessMessage() throws Exception {
        GameRequest request = new GameRequest();
        request.setGameName("Valorant");
        request.setGameDescription("Tactical shooter");
        request.setCategory("FPS");
        request.setGameIcon("icon.png");
        request.setRating(4.5f);
        when(adminService.addGame(any(), any())).thenReturn(ok("Game 'Valorant' added successfully"));

        mockMvc.perform(post("/admin/add/game")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testAddGame_whenNameMissing_shouldReturn400() throws Exception {
        GameRequest request = new GameRequest();
        request.setGameDescription("Tactical shooter");
        request.setCategory("FPS");
        request.setRating(4.5f);

        mockMvc.perform(post("/admin/add/game")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(adminService, never()).addGame(any(), any());
    }

    @Test
    void testAddKeyword_whenValidKeywordProvided_shouldReturnSuccessMessage() throws Exception {
        KeywordRequest request = new KeywordRequest();
        request.setKeyword("Competitive");
        request.setDescription("Plays to win");
        when(adminService.addKeyword(any(), any())).thenReturn(ok("Keyword 'Competitive' added successfully"));

        mockMvc.perform(post("/admin/add/keyword")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
