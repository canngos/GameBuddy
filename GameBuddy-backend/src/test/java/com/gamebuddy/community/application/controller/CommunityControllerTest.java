package com.gamebuddy.community.application.controller;

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
import com.gamebuddy.community.domain.service.CommunityService;
import com.gamebuddy.community.interfaces.dto.*;
import com.gamebuddy.community.interfaces.request.CommunityRequest;
import com.gamebuddy.community.interfaces.request.CreateCommentRequest;
import com.gamebuddy.community.interfaces.request.CreateCommunityRequest;
import com.gamebuddy.community.interfaces.request.PostRequest;
import com.gamebuddy.community.interfaces.response.*;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * The security filter chain is loaded (so {@code @AuthenticationPrincipal} resolves) but
 * not applied, with the principal placed directly into the {@code SecurityContextHolder}.
 */
@WebMvcTest(CommunityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CommunityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommunityService communityService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer principal;

    @BeforeEach
    void setUp() {
        principal = new Gamer();
        principal.setUserId(UUID.randomUUID().toString());
        principal.setEmail("member@example.com");
        principal.setGamerUsername("member");

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
    void testGetCommunities_whenCalled_ReturnsCommunities() throws Exception {
        CommunityResponse response = new CommunityResponse();
        CommunityResponseBody body = new CommunityResponseBody();
        CommunityDto dto = new CommunityDto();
        dto.setCommunityId(UUID.randomUUID().toString());
        dto.setName("Valorant TR");
        dto.setIsJoined(true);
        body.setCommunities(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(communityService.getCommunities(any())).thenReturn(response);

        mockMvc.perform(get("/community/get/communities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.communities[0].name").value("Valorant TR"));
    }

    @Test
    @DisplayName("the member list endpoint now passes the caller, so the service can check membership")
    void testGetMembers_whenCalled_PassesThePrincipal() throws Exception {
        String communityId = UUID.randomUUID().toString();
        MemberResponse response = new MemberResponse();
        MemberResponseBody body = new MemberResponseBody();
        body.setMembers(List.of());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(communityService.getMembers(any(), eq(communityId))).thenReturn(response);

        mockMvc.perform(get("/community/get/members/" + communityId)).andExpect(status().isOk());

        verify(communityService).getMembers(argThat(g -> g.getUserId().equals(principal.getUserId())), eq(communityId));
    }

    @Test
    void testGetCommunitiesPosts_whenCalled_PassesPageable() throws Exception {
        String communityId = UUID.randomUUID().toString();
        when(communityService.getCommunitiesPosts(any(), eq(communityId), any(Pageable.class)))
                .thenReturn(emptyPosts());

        mockMvc.perform(get("/community/get/posts/" + communityId)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(communityService).getCommunitiesPosts(any(), eq(communityId), any(Pageable.class));
    }

    @Test
    void testGetJoinedCommunitiesPosts_whenCalled_ReturnsFeed() throws Exception {
        when(communityService.getJoinedCommunitiesPosts(any(), any(Pageable.class)))
                .thenReturn(emptyPosts());

        mockMvc.perform(get("/community/get/posts")).andExpect(status().isOk());
    }

    @Test
    void testGetPostComments_whenCalled_PassesThePrincipal() throws Exception {
        String postId = UUID.randomUUID().toString();
        CommentsResponse response = new CommentsResponse();
        CommentsResponseBody body = new CommentsResponseBody();
        CommentDto dto = new CommentDto();
        dto.setCommentId(UUID.randomUUID().toString());
        dto.setMessage("nice");
        body.setComments(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(communityService.getPostComments(any(), eq(postId))).thenReturn(response);

        mockMvc.perform(get("/community/get/comments/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.comments[0].message").value("nice"));
    }

    @Test
    void testCreateCommunity_whenValidBody_ReturnsSuccess() throws Exception {
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setName("New");
        request.setDescription("desc");
        when(communityService.createCommunity(any(), any())).thenReturn(ok("Community created successfully"));

        mockMvc.perform(post("/community/create/community")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a community without a name is rejected before the service is reached")
    void testCreateCommunity_whenNameMissing_ShouldReturn400() throws Exception {
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setDescription("desc");

        mockMvc.perform(post("/community/create/community")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(communityService, never()).createCommunity(any(), any());
    }

    @Test
    void testCreatePost_whenValidBody_ReturnsSuccess() throws Exception {
        PostRequest request = new PostRequest();
        request.setCommunityId(UUID.randomUUID().toString());
        request.setTitle("title");
        request.setBody("body");
        when(communityService.createPost(any(), any())).thenReturn(ok("Post created successfully"));

        mockMvc.perform(post("/community/create/post")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an empty comment is rejected: the request had no validation at all")
    void testCreateComment_whenMessageBlank_ShouldReturn400() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(UUID.randomUUID().toString());
        request.setMessage("");

        mockMvc.perform(post("/community/create/comment")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(communityService, never()).createComment(any(), any());
    }

    @Test
    void testCreateComment_whenValidBody_ReturnsSuccess() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(UUID.randomUUID().toString());
        request.setMessage("nice");
        when(communityService.createComment(any(), any())).thenReturn(ok("Comment created successfully"));

        mockMvc.perform(post("/community/create/comment")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteCommunity_whenValidBody_ReturnsSuccess() throws Exception {
        CommunityRequest request = new CommunityRequest();
        request.setCommunityId(UUID.randomUUID().toString());
        when(communityService.deleteCommunity(any(), any())).thenReturn(ok("Community deleted successfully"));

        mockMvc.perform(delete("/community/delete/community")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testDeletePost_whenCalled_PassesThePrincipal() throws Exception {
        String postId = UUID.randomUUID().toString();
        when(communityService.deletePost(any(), eq(postId))).thenReturn(ok("Post deleted successfully"));

        mockMvc.perform(delete("/community/delete/post/" + postId)).andExpect(status().isOk());

        verify(communityService).deletePost(argThat(g -> g.getUserId().equals(principal.getUserId())), eq(postId));
    }

    @Test
    void testDeleteComment_whenCalled_ReturnsSuccess() throws Exception {
        String commentId = UUID.randomUUID().toString();
        when(communityService.deleteComment(any(), eq(commentId))).thenReturn(ok("Comment deleted successfully"));

        mockMvc.perform(delete("/community/delete/comment/" + commentId)).andExpect(status().isOk());
    }

    @Test
    void testJoinCommunity_whenValidBody_ReturnsSuccess() throws Exception {
        CommunityRequest request = new CommunityRequest();
        request.setCommunityId(UUID.randomUUID().toString());
        when(communityService.joinCommunity(any(), any())).thenReturn(ok("Joined successfully"));

        mockMvc.perform(post("/community/join/community")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testLeaveCommunity_whenValidBody_ReturnsSuccess() throws Exception {
        CommunityRequest request = new CommunityRequest();
        request.setCommunityId(UUID.randomUUID().toString());
        when(communityService.leaveCommunity(any(), any())).thenReturn(ok("Left successfully"));

        mockMvc.perform(post("/community/leave/community")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testLikePost_whenCalled_ReturnsSuccess() throws Exception {
        String postId = UUID.randomUUID().toString();
        when(communityService.likePost(any(), eq(postId))).thenReturn(ok("Liked post successfully"));

        mockMvc.perform(post("/community/like/post/" + postId)).andExpect(status().isOk());
    }

    @Test
    void testUnlikePost_whenCalled_ReturnsSuccess() throws Exception {
        String postId = UUID.randomUUID().toString();
        when(communityService.unlikePost(any(), eq(postId))).thenReturn(ok("Unliked post successfully"));

        mockMvc.perform(post("/community/unlike/post/" + postId)).andExpect(status().isOk());
    }

    @Test
    void testLikeComment_whenCalled_ReturnsSuccess() throws Exception {
        String commentId = UUID.randomUUID().toString();
        when(communityService.likeComment(any(), eq(commentId))).thenReturn(ok("Liked comment successfully"));

        mockMvc.perform(post("/community/like/comment/" + commentId)).andExpect(status().isOk());
    }

    @Test
    void testUnlikeComment_whenCalled_ReturnsSuccess() throws Exception {
        String commentId = UUID.randomUUID().toString();
        when(communityService.unlikeComment(any(), eq(commentId))).thenReturn(ok("Unliked comment successfully"));

        mockMvc.perform(post("/community/unlike/comment/" + commentId)).andExpect(status().isOk());
    }

    @Test
    void testGetPostLikes_whenCalled_ReturnsMembers() throws Exception {
        String postId = UUID.randomUUID().toString();
        MemberResponse response = new MemberResponse();
        MemberResponseBody body = new MemberResponseBody();
        GamerDto dto = new GamerDto();
        dto.setUserId(UUID.randomUUID().toString());
        dto.setGamerUsername("liker");
        body.setMembers(List.of(dto));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        when(communityService.getPostLikes(any(), eq(postId))).thenReturn(response);

        mockMvc.perform(get("/community/get/post/likes/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.members[0].gamerUsername").value("liker"));
    }

    private static PostResponse emptyPosts() {
        PostResponse response = new PostResponse();
        PostResponseBody body = new PostResponseBody();
        body.setPosts(List.of());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
