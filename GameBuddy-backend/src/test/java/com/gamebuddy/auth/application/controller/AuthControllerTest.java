package com.gamebuddy.auth.application.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gamebuddy.auth.domain.service.AuthService;
import com.gamebuddy.auth.interfaces.dto.*;
import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.shared.entity.Gamer;
import java.time.LocalDate;
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

/**
 * The security filter chain is loaded (so {@code @AuthenticationPrincipal} resolves)
 * but not applied ({@code addFilters = false}), and the principal is placed directly
 * into the {@code SecurityContextHolder}.
 *
 * <p>{@link GlobalExceptionHandler} lives in {@code :common} and reaches production
 * through {@code CommonAutoConfiguration}; the {@code @WebMvcTest} slice does not load
 * that auto-configuration, so it is imported explicitly here.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

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
        principal.setGamerUsername("tester");
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

    private static DefaultMessageResponse ok(String message) {
        return DefaultMessageResponse.of(message);
    }

    @Test
    void testLogin_whenValidLoginRequestProvided_shouldReturnTokenAndUserId() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("test@example.com");
        request.setPassword("Str0ngPassw0rd");

        LoginResponse response = new LoginResponse();
        LoginResponseBody body = new LoginResponseBody();
        body.setAccessToken("jwt");
        body.setUserId(principal.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.accessToken").value("jwt"))
                .andExpect(jsonPath("$.status.code").value("100"));
    }

    @Test
    @DisplayName("a password below the policy length is rejected with 400 before reaching the service")
    void testLogin_whenBodyInvalid_shouldReturn400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("");
        request.setPassword("");

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(authService, never()).login(any());
    }

    @Test
    void testRegister_whenValidRegisterRequestProvided_shouldReturnUserId() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("Str0ngPassw0rd");
        request.setFcmToken("fcm");

        RegisterResponse response = new RegisterResponse();
        RegisterResponseBody body = new RegisterResponseBody();
        body.setUserId(principal.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body.data.userId").value(principal.getUserId()));
    }

    @Test
    @DisplayName("registration rejects a password that fails the length policy")
    void testRegister_whenPasswordTooShort_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("short");
        request.setFcmToken("fcm");

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(authService, never()).register(any());
    }

    @Test
    void testVerifyCode_whenValidVerifyRequestProvided_shouldReturnTokenAndUserId() throws Exception {
        VerifyRequest request = new VerifyRequest();
        request.setEmail("test@example.com");
        request.setVerificationCode(123456);

        VerifyResponse response = new VerifyResponse();
        VerifyResponseBody body = new VerifyResponseBody();
        body.setAccessToken("jwt");
        body.setUserId(principal.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(authService.verifyCode(any())).thenReturn(response);

        mockMvc.perform(post("/auth/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.accessToken").value("jwt"));
    }

    @Test
    @DisplayName("a non-six-digit code never reaches the service")
    void testVerifyCode_whenCodeOutOfRange_shouldReturn400() throws Exception {
        VerifyRequest request = new VerifyRequest();
        request.setEmail("test@example.com");
        request.setVerificationCode(42);

        mockMvc.perform(post("/auth/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(authService, never()).verifyCode(any());
    }

    @Test
    void testSendCode_whenValidEmailProvided_shouldReturnSuccessMessage() throws Exception {
        SendCodeRequest request = new SendCodeRequest();
        request.setEmail("test@example.com");
        request.setIsRegister(true);
        when(authService.sendVerificationEmail(any())).thenReturn(ok("Verification code sent successfully"));

        mockMvc.perform(post("/auth/sendCode")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("100"));
    }

    @Test
    void testValidateToken_whenValidTokenProvided_shouldReturnUsernameAndTrue() throws Exception {
        TokenResponse response = new TokenResponse();
        TokenResponseBody body = new TokenResponseBody();
        body.setUsername("tester");
        body.setIsValid(true);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(authService.validateToken("abc.def.ghi")).thenReturn(response);

        mockMvc.perform(post("/auth/validateToken").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.isValid").value(true));
    }

    @Test
    @DisplayName("a malformed Authorization header is a clean 401, not the 500 that substring(7) produced")
    void testValidateToken_whenHeaderMalformed_shouldNotReturn500() throws Exception {
        mockMvc.perform(post("/auth/validateToken").header("Authorization", "abc"))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).validateToken(anyString());
    }

    @Test
    void testValidateToken_whenHeaderMissing_shouldNotReturn500() throws Exception {
        mockMvc.perform(post("/auth/validateToken")).andExpect(status().isUnauthorized());
    }

    @Test
    void testSetUsername_whenValidUsernameProvided_shouldReturnSuccessMessage() throws Exception {
        UsernameRequest request = new UsernameRequest();
        request.setUsername("newname");
        when(authService.setUsername(any(), any())).thenReturn(ok("Username set successfully"));

        mockMvc.perform(post("/auth/username")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).setUsername(argThat(g -> g.getUserId().equals(principal.getUserId())), any());
    }

    @Test
    void testDetails_whenValidDetailsProvided_shouldReturnSuccessMessage() throws Exception {
        DetailsRequest request = new DetailsRequest();
        request.setBirthDate(LocalDate.of(1998, 8, 24));
        request.setCountry("TR");
        request.setGender("M");
        request.setAvatar(UUID.randomUUID().toString());
        request.setFavoriteGames(List.of("g1", "g2", "g3"));
        request.setKeywords(List.of("k1", "k2", "k3", "k4", "k5"));
        when(authService.details(any(), any())).thenReturn(ok("User details saved successfully"));

        mockMvc.perform(post("/auth/details")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("fewer than three games is rejected at the edge")
    void testDetails_whenTooFewGames_shouldReturn400() throws Exception {
        DetailsRequest request = new DetailsRequest();
        request.setBirthDate(LocalDate.of(1998, 8, 24));
        request.setCountry("TR");
        request.setAvatar(UUID.randomUUID().toString());
        request.setFavoriteGames(List.of("g1"));
        request.setKeywords(List.of("k1", "k2", "k3", "k4", "k5"));

        mockMvc.perform(post("/auth/details")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(authService, never()).details(any(), any());
    }

    @Test
    void testChangePwd_whenValidPasswordProvided_shouldReturnSuccessMessage() throws Exception {
        ChangePwdRequest request = new ChangePwdRequest();
        request.setCurrentPassword("Str0ngPassw0rd");
        request.setPassword("N3wPassw0rd");
        when(authService.changePwd(any(), any())).thenReturn(ok("Password changed successfully."));

        mockMvc.perform(put("/auth/change/pwd")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("omitting the current password is rejected")
    void testChangePwd_whenCurrentPasswordMissing_shouldReturn400() throws Exception {
        ChangePwdRequest request = new ChangePwdRequest();
        request.setPassword("N3wPassw0rd");

        mockMvc.perform(put("/auth/change/pwd")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(authService, never()).changePwd(any(), any());
    }

    @Test
    void testChangeAvatar_whenValidAvatarIdProvided_shouldReturnSuccessMessage() throws Exception {
        ChangeAvatarRequest request = new ChangeAvatarRequest();
        request.setAvatarId(UUID.randomUUID().toString());
        when(authService.changeAvatar(any(), any())).thenReturn(ok("Avatar changed successfully"));

        mockMvc.perform(put("/auth/change/avatar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testChangeAge_whenValidAgeProvided_shouldReturnSuccessMessage() throws Exception {
        ChangeAgeRequest request = new ChangeAgeRequest();
        request.setBirthDate(LocalDate.of(1998, 8, 24));
        when(authService.changeAge(any(), any())).thenReturn(ok("Date of birth changed successfully"));

        mockMvc.perform(put("/auth/change/age")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testChangeGames_whenValidGamesProvided_shouldReturnSuccessMessage() throws Exception {
        ChangeDetailRequest request = new ChangeDetailRequest();
        request.setGamesOrKeywordsList(List.of("g1", "g2", "g3"));
        when(authService.changeGames(any(), any())).thenReturn(ok("Games changed successfully"));

        mockMvc.perform(put("/auth/change/games")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testChangeKeywords_whenValidKeywordsProvided_shouldReturnSuccessMessage() throws Exception {
        ChangeDetailRequest request = new ChangeDetailRequest();
        request.setGamesOrKeywordsList(List.of("k1", "k2", "k3", "k4", "k5"));
        when(authService.changeKeywords(any(), any())).thenReturn(ok("Keywords changed successfully"));

        mockMvc.perform(put("/auth/change/keywords")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
