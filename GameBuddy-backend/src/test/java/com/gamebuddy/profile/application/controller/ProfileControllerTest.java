package com.gamebuddy.profile.application.controller;

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
import com.gamebuddy.profile.domain.service.AvatarUploadService;
import com.gamebuddy.profile.domain.service.ProfileService;
import com.gamebuddy.profile.interfaces.dto.*;
import com.gamebuddy.profile.interfaces.request.FriendRequest;
import com.gamebuddy.profile.interfaces.response.*;
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

/**
 * The security filter chain is loaded (so {@code @AuthenticationPrincipal} resolves) but
 * not applied, with the principal placed directly into the {@code SecurityContextHolder}.
 *
 * <p>{@link GlobalExceptionHandler} reaches production through {@code
 * CommonAutoConfiguration}, which a {@code @WebMvcTest} slice does not load, so it is
 * imported explicitly.
 */
@WebMvcTest(ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProfileService profileService;

    // A second collaborator of the controller since avatars became uploads. Without it
    // the whole context fails to load, which shows up as every test in the class failing
    // for reasons unrelated to what they assert.
    @MockitoBean
    private AvatarUploadService avatarUploadService;

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

    private static DefaultMessageResponse ok(String message) {
        return DefaultMessageResponse.of(message);
    }

    private static FriendRequest friendRequest(String userId) {
        FriendRequest r = new FriendRequest();
        r.setUserId(userId);
        return r;
    }

    @Test
    @DisplayName("the own-profile endpoint passes the authenticated id, never a client-supplied one")
    void testGetOwnInfo_whenCalled_UsesThePrincipalsId() throws Exception {
        UserInfoResponse response = new UserInfoResponse();
        UserInfoResponseBody body = new UserInfoResponseBody();
        body.setUsername("me");
        body.setEmail("me@example.com");
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(profileService.getUserInfo(any(), eq(principal.getUserId()))).thenReturn(response);

        mockMvc.perform(get("/application/get/user/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.email").value("me@example.com"));

        verify(profileService)
                .getUserInfo(argThat(g -> g.getUserId().equals(principal.getUserId())), eq(principal.getUserId()));
    }

    @Test
    void testGetUserInfo_whenViewingAnotherUser_PassesBothIds() throws Exception {
        String otherId = UUID.randomUUID().toString();
        UserInfoResponse response = new UserInfoResponse();
        UserInfoResponseBody body = new UserInfoResponseBody();
        body.setUsername("other");
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(profileService.getUserInfo(any(), eq(otherId))).thenReturn(response);

        mockMvc.perform(get("/application/get/user/info/" + otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.username").value("other"))
                .andExpect(jsonPath("$.body.data.email").doesNotExist());
    }

    @Test
    void testGetKeywords_whenCalled_ReturnsKeywords() throws Exception {
        KeywordsResponse response = new KeywordsResponse();
        KeywordsResponseBody body = new KeywordsResponseBody();
        KeywordsDto dto = new KeywordsDto();
        dto.setId(UUID.randomUUID());
        dto.setKeywordName("Competitive");
        body.setKeywords(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(profileService.getKeywords()).thenReturn(response);

        mockMvc.perform(get("/application/get/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.keywords[0].keywordName").value("Competitive"));
    }

    @Test
    void testGetGames_whenCalled_ReturnsGames() throws Exception {
        GamesResponse response = new GamesResponse();
        GamesResponseBody body = new GamesResponseBody();
        GamesDto dto = new GamesDto();
        dto.setGameId("g1");
        dto.setGameName("Valorant");
        body.setGames(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(profileService.getGames()).thenReturn(response);

        mockMvc.perform(get("/application/get/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.games[0].gameName").value("Valorant"));
    }

    @Test
    void testGetAvatars_whenCalled_ReturnsAvatars() throws Exception {
        AvatarsResponse response = new AvatarsResponse();
        AvatarsResponseBody body = new AvatarsResponseBody();
        AvatarsDto dto = new AvatarsDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setImage("img.png");
        body.setAvatars(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(profileService.getAvatars()).thenReturn(response);

        mockMvc.perform(get("/application/get/avatars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.avatars[0].image").value("img.png"));
    }

    // Collecting moved to BadgeController along with achievements; see BadgeControllerTest.
    // Buying moved to CosmeticController; see CosmeticControllerTest.

    @Test
    void testGetFriends_whenCalled_ReturnsFriends() throws Exception {
        FriendsResponse response = new FriendsResponse();
        FriendsResponseBody body = new FriendsResponseBody();
        GamerDto dto = new GamerDto();
        dto.setUserId(UUID.randomUUID().toString());
        dto.setUsername("buddy");
        body.setFriends(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(profileService.getFriends(any())).thenReturn(response);

        mockMvc.perform(get("/application/get/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.friends[0].username").value("buddy"));
    }

    @Test
    void testAcceptFriend_whenValidBody_DelegatesWithPrincipal() throws Exception {
        String targetId = UUID.randomUUID().toString();
        when(profileService.acceptFriend(any(), any())).thenReturn(ok("Friend added successfully"));

        mockMvc.perform(post("/application/accept/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(friendRequest(targetId))))
                .andExpect(status().isOk());

        verify(profileService)
                .acceptFriend(argThat(g -> g.getUserId().equals(principal.getUserId())), any(FriendRequest.class));
    }

    @Test
    @DisplayName("an empty userId is rejected before the service is reached")
    void testAcceptFriend_whenUserIdBlank_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/application/accept/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(friendRequest(""))))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).acceptFriend(any(), any());
    }

    @Test
    void testBlockFriend_whenValidBody_DelegatesWithPrincipal() throws Exception {
        when(profileService.blockUser(any(), any())).thenReturn(ok("User blocked successfully"));

        mockMvc.perform(post("/application/block/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                friendRequest(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void testSendFriendRequest_whenValidBody_DelegatesWithPrincipal() throws Exception {
        when(profileService.sendFriendRequest(any(), any())).thenReturn(ok("Friend request sent successfully"));

        mockMvc.perform(post("/application/send/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                friendRequest(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void testRemoveFriend_whenValidBody_DelegatesWithPrincipal() throws Exception {
        when(profileService.removeFriend(any(), any())).thenReturn(ok("Friend removed successfully"));

        mockMvc.perform(post("/application/remove/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                friendRequest(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void testUnblockFriend_whenValidBody_DelegatesWithPrincipal() throws Exception {
        when(profileService.unblockUser(any(), any())).thenReturn(ok("User unblocked successfully"));

        mockMvc.perform(post("/application/unblock/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                friendRequest(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void testRejectFriend_whenValidBody_DelegatesWithPrincipal() throws Exception {
        when(profileService.rejectFriend(any(), any())).thenReturn(ok("Friend rejected successfully"));

        mockMvc.perform(post("/application/reject/friend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                friendRequest(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
    }
}
