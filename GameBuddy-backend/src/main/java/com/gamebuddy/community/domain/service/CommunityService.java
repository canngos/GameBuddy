package com.gamebuddy.community.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.community.interfaces.request.CommunityRequest;
import com.gamebuddy.community.interfaces.request.CreateCommentRequest;
import com.gamebuddy.community.interfaces.request.CreateCommunityRequest;
import com.gamebuddy.community.interfaces.request.PostRequest;
import com.gamebuddy.community.interfaces.response.*;
import com.gamebuddy.shared.entity.Gamer;
import org.springframework.data.domain.Pageable;

/**
 * Every operation now takes the authenticated {@link Gamer}, including the ones that used
 * to take no principal at all.
 *
 * <p>{@code getMembers}, {@code getPostLikes}, {@code getCommentLikes} and
 * {@code getPostComments} previously took only an id. Having no idea who was asking, they
 * could not check membership, so any authenticated user could read the member list,
 * comments and likes of any community they had never joined.
 */
public interface CommunityService {

    CommunityResponse getCommunities(Gamer principal);

    MemberResponse getMembers(Gamer principal, String communityId);

    PostResponse getCommunitiesPosts(Gamer principal, String communityId, Pageable pageable);

    /**
     * A single post.
     *
     * <p>Added for the client's post screen, which shows a post above its comments. Until
     * now the only way to obtain a post was to page a feed until it appeared, so a client
     * could open a post only while the list it came from was still in memory — a detail
     * screen that could not be reloaded, deep-linked, or opened from a notification.
     */
    PostResponse getPost(Gamer principal, String postId);

    MemberResponse getPostLikes(Gamer principal, String postId);

    MemberResponse getCommentLikes(Gamer principal, String commentId);

    PostResponse getJoinedCommunitiesPosts(Gamer principal, Pageable pageable);

    CommentsResponse getPostComments(Gamer principal, String postId);

    DefaultMessageResponse createPost(Gamer principal, PostRequest postRequest);

    DefaultMessageResponse createCommunity(Gamer principal, CreateCommunityRequest communityRequest);

    DefaultMessageResponse createComment(Gamer principal, CreateCommentRequest commentRequest);

    DefaultMessageResponse deleteCommunity(Gamer principal, CommunityRequest communityRequest);

    DefaultMessageResponse deletePost(Gamer principal, String postId);

    DefaultMessageResponse deleteComment(Gamer principal, String commentId);

    DefaultMessageResponse joinCommunity(Gamer principal, CommunityRequest communityRequest);

    DefaultMessageResponse leaveCommunity(Gamer principal, CommunityRequest communityRequest);

    /**
     * Hands a community to another member.
     *
     * <p>Ownership had no succession path: an owner could not leave, and an owner whose
     * account went away left the community unadministrable for good.
     */
    DefaultMessageResponse transferOwnership(Gamer principal, String communityId, String newOwnerId);

    DefaultMessageResponse likePost(Gamer principal, String postId);

    DefaultMessageResponse likeComment(Gamer principal, String commentId);

    DefaultMessageResponse unlikePost(Gamer principal, String postId);

    DefaultMessageResponse unlikeComment(Gamer principal, String commentId);
}
