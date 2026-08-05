package com.gamebuddy.notif.application.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.security.InternalApiKeyFilter;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.notif.domain.service.NotificationService;
import com.gamebuddy.notif.interfaces.dto.GetNotificationsResponseBody;
import com.gamebuddy.notif.interfaces.dto.NotificationDto;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import com.gamebuddy.notif.interfaces.response.GetNotificationsResponse;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private InternalApiKeyFilter internalApiKeyFilter;

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

    private static SendNotificationTokenRequest tokenRequest(String title) {
        SendNotificationTokenRequest r = new SendNotificationTokenRequest();
        r.setToken("device-token");
        r.setTitle(title);
        r.setBody("Body");
        return r;
    }

    @Test
    @DisplayName("the history endpoint takes no user id: it serves the authenticated caller only")
    void testShowAll_whenCalled_UsesThePrincipal() throws Exception {
        GetNotificationsResponse response = new GetNotificationsResponse();
        GetNotificationsResponseBody body = new GetNotificationsResponseBody();
        NotificationDto dto = new NotificationDto();
        dto.setTitle("Personal");
        dto.setBody("Body");
        dto.setUserIdOrTopic(principal.getUserId());
        dto.setIsTopic(false);
        dto.setCreatedDate(Instant.now());
        body.setUserNotifications(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(notificationService.showAll(any(), any(Pageable.class))).thenReturn(response);

        mockMvc.perform(get("/notif/showall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.userNotifications[0].title").value("Personal"));

        verify(notificationService)
                .showAll(argThat(g -> g.getUserId().equals(principal.getUserId())), any(Pageable.class));
    }

    @Test
    @DisplayName("the old /notif/showall/{userId} route is gone")
    void testShowAll_whenCalledWithAUserIdInThePath_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/notif/showall/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
