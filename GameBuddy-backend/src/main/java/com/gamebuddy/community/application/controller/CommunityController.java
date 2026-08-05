package com.gamebuddy.community.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.community.domain.service.CommunityService;
import com.gamebuddy.community.interfaces.request.CommunityRequest;
import com.gamebuddy.community.interfaces.request.CreateCommentRequest;
import com.gamebuddy.community.interfaces.request.CreateCommunityRequest;
import com.gamebuddy.community.interfaces.request.PostRequest;
import com.gamebuddy.community.interfaces.response.*;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The authenticated principal arrives via {@link AuthenticationPrincipal}.
 *
 * <p>Every method used to take a {@code @RequestHeader("Authorization")} and pass
 * {@code token.substring(7)} down — an HTTP 500 for any header shorter than seven
 * characters, and a re-parse of a token the filter had already validated. Several
 * endpoints took the header and then ignored it, passing only an id to a service method
 * that had no way to tell who was asking.
 */
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping("/get/communities")
    public ResponseEntity<CommunityResponse> getCommunities(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(communityService.getCommunities(principal));
    }

    @GetMapping("/get/members/{communityId}")
    public ResponseEntity<MemberResponse> getMembers(
            @AuthenticationPrincipal Gamer principal, @PathVariable String communityId) {
        return ResponseEntity.ok(communityService.getMembers(principal, communityId));
    }

    @GetMapping("/get/posts/{communityId}")
    public ResponseEntity<PostResponse> getCommunitiesPosts(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String communityId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(communityService.getCommunitiesPosts(principal, communityId, pageable));
    }

    /** One post. Answers as a one-element list — see {@code CommunityService#getPost}. */
    @GetMapping("/get/post/{postId}")
    public ResponseEntity<PostResponse> getPost(@AuthenticationPrincipal Gamer principal, @PathVariable String postId) {
        return ResponseEntity.ok(communityService.getPost(principal, postId));
    }

    /** The home feed. Paged: it used to return every post of every joined community. */
    @GetMapping("/get/posts")
    public ResponseEntity<PostResponse> getJoinedCommunitiesPosts(
            @AuthenticationPrincipal Gamer principal, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(communityService.getJoinedCommunitiesPosts(principal, pageable));
    }

    @GetMapping("/get/comments/{postId}")
    public ResponseEntity<CommentsResponse> getPostComments(
            @AuthenticationPrincipal Gamer principal, @PathVariable String postId) {
        return ResponseEntity.ok(communityService.getPostComments(principal, postId));
    }

    @GetMapping("/get/post/likes/{postId}")
    public ResponseEntity<MemberResponse> getPostLikes(
            @AuthenticationPrincipal Gamer principal, @PathVariable String postId) {
        return ResponseEntity.ok(communityService.getPostLikes(principal, postId));
    }

    @GetMapping("/get/comment/likes/{commentId}")
    public ResponseEntity<MemberResponse> getCommentLikes(
            @AuthenticationPrincipal Gamer principal, @PathVariable String commentId) {
        return ResponseEntity.ok(communityService.getCommentLikes(principal, commentId));
    }

    @PostMapping("/create/community")
    public ResponseEntity<DefaultMessageResponse> createCommunity(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody CreateCommunityRequest request) {
        return ResponseEntity.ok(communityService.createCommunity(principal, request));
    }

    @PostMapping("/create/post")
    public ResponseEntity<DefaultMessageResponse> createPost(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody PostRequest request) {
        return ResponseEntity.ok(communityService.createPost(principal, request));
    }

    @PostMapping("/create/comment")
    public ResponseEntity<DefaultMessageResponse> createComment(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(communityService.createComment(principal, request));
    }

    @DeleteMapping("/delete/community")
    public ResponseEntity<DefaultMessageResponse> deleteCommunity(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody CommunityRequest request) {
        return ResponseEntity.ok(communityService.deleteCommunity(principal, request));
    }

    @DeleteMapping("/delete/post/{postId}")
    public ResponseEntity<DefaultMessageResponse> deletePost(
            @AuthenticationPrincipal Gamer principal, @PathVariable String postId) {
        return ResponseEntity.ok(communityService.deletePost(principal, postId));
    }

    @DeleteMapping("/delete/comment/{commentId}")
    public ResponseEntity<DefaultMessageResponse> deleteComment(
            @AuthenticationPrincipal Gamer principal, @PathVariable String commentId) {
        return ResponseEntity.ok(communityService.deleteComment(principal, commentId));
    }

    @PostMapping("/join/community")
    public ResponseEntity<DefaultMessageResponse> joinCommunity(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody CommunityRequest request) {
        return ResponseEntity.ok(communityService.joinCommunity(principal, request));
    }

    @PostMapping("/leave/community")
    public ResponseEntity<DefaultMessageResponse> leaveCommunity(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody CommunityRequest request) {
        return ResponseEntity.ok(communityService.leaveCommunity(principal, request));
    }

    /** Hands the community to another member. Owner, or an admin. */
    @PostMapping("/transfer/community/{communityId}/to/{newOwnerId}")
    public ResponseEntity<DefaultMessageResponse> transferOwnership(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String communityId,
            @PathVariable String newOwnerId) {
        return ResponseEntity.ok(communityService.transferOwnership(principal, communityId, newOwnerId));
    }

    @PostMapping("/like/post/{postId}")
    public ResponseEntity<DefaultMessageResponse> likePost(
            @AuthenticationPrincipal Gamer principal, @PathVariable String postId) {
        return ResponseEntity.ok(communityService.likePost(principal, postId));
    }

    @PostMapping("/like/comment/{commentId}")
    public ResponseEntity<DefaultMessageResponse> likeComment(
            @AuthenticationPrincipal Gamer principal, @PathVariable String commentId) {
        return ResponseEntity.ok(communityService.likeComment(principal, commentId));
    }

    @PostMapping("/unlike/post/{postId}")
    public ResponseEntity<DefaultMessageResponse> unlikePost(
            @AuthenticationPrincipal Gamer principal, @PathVariable String postId) {
        return ResponseEntity.ok(communityService.unlikePost(principal, postId));
    }

    @PostMapping("/unlike/comment/{commentId}")
    public ResponseEntity<DefaultMessageResponse> unlikeComment(
            @AuthenticationPrincipal Gamer principal, @PathVariable String commentId) {
        return ResponseEntity.ok(communityService.unlikeComment(principal, commentId));
    }
}
