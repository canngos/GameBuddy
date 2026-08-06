package com.gamebuddy.community.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.community.application.mapper.CommunityMapper;
import com.gamebuddy.community.infrastructure.entity.*;
import com.gamebuddy.community.infrastructure.repository.*;
import com.gamebuddy.community.interfaces.dto.*;
import com.gamebuddy.community.interfaces.request.CommunityRequest;
import com.gamebuddy.community.interfaces.request.CreateCommentRequest;
import com.gamebuddy.community.interfaces.request.CreateCommunityRequest;
import com.gamebuddy.community.interfaces.request.PostRequest;
import com.gamebuddy.community.interfaces.response.*;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Communities, posts, comments and likes.
 *
 * <p>The recurring theme of this rewrite is authorisation. Only {@code createPost} and
 * {@code getCommunitiesPosts} checked membership; reading a community's members, comments
 * and likes, and writing comments and likes, checked nothing at all — so any
 * authenticated account could read and participate in every community it had never
 * joined. {@link #requireMember} is now applied on every path that touches community
 * content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultCommunityService implements CommunityService {

    private final CommunityRepository communityRepository;
    private final GamerRepository gamerRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AvatarsRepository avatarsRepository;
    private final CommunityMapper communityMapper;
    private final AvatarUrls avatarUrls;
    private final CosmeticUrls cosmeticUrls;
    private final ApplicationEventPublisher events;

    // =======================================================================
    // Reads
    // =======================================================================

    /** The community directory is deliberately visible to members and non-members alike. */
    @Override
    @Transactional(readOnly = true)
    public CommunityResponse getCommunities(Gamer principal) {
        Gamer gamer = reload(principal);

        List<CommunityDto> communities = communityRepository.findAll().stream()
                .map(community -> {
                    CommunityDto dto = communityMapper.toDto(community);
                    dto.setIsJoined(community.hasMember(gamer));
                    return dto;
                })
                .toList();

        CommunityResponseBody body = new CommunityResponseBody();
        body.setCommunities(communities);
        return respond(new CommunityResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberResponse getMembers(Gamer principal, String communityId) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(communityId);
        // Who is in a community is information about its members, not a public directory.
        requireMember(community, gamer);

        MemberResponseBody body = new MemberResponseBody();
        body.setMembers(toGamerDtos(community.getMembers(), community.getOwner()));
        return respond(new MemberResponse(), body);
    }

    /**
     * A community's feed.
     *
     * <p>A non-member used to receive HTTP 200 with an empty post list, indistinguishable
     * from an empty community. It is a 403 now.
     */
    @Override
    @Transactional(readOnly = true)
    public PostResponse getCommunitiesPosts(Gamer principal, String communityId, Pageable pageable) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(communityId);
        requireMember(community, gamer);

        List<Post> posts = postRepository.findAllByCommunityOrderByUpdatedDateDesc(community, pageable);
        return postResponse(posts, gamer);
    }

    /**
     * One post, for the screen that shows it above its comments.
     *
     * <p>Answers with the same {@code PostResponse} as the feeds — a list of one — so the
     * DTO, the avatar resolution and the per-caller {@code isLiked} are the one
     * implementation rather than a second copy that can drift from it.
     */
    @Override
    @Transactional(readOnly = true)
    public PostResponse getPost(Gamer principal, String postId) {
        Gamer gamer = reload(principal);
        Post post = requirePost(postId);
        requireMember(post.getCommunity(), gamer);

        return postResponse(List.of(post), gamer);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getJoinedCommunitiesPosts(Gamer principal, Pageable pageable) {
        Gamer gamer = reload(principal);
        // Community.members is the owning side of the join table; see CommunityRepository.
        List<Community> communities = communityRepository.findAllByMembersContaining(gamer);
        if (communities.isEmpty()) {
            return postResponse(List.of(), gamer);
        }

        // Ordered and paged in the database. This used to pull every post of every
        // joined community into memory and sort the resulting DTO list afterwards.
        List<Post> posts = postRepository.findAllByCommunityInOrderByUpdatedDateDesc(communities, pageable);
        return postResponse(posts, gamer);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentsResponse getPostComments(Gamer principal, String postId) {
        Gamer gamer = reload(principal);
        Post post = requirePost(postId);
        requireMember(post.getCommunity(), gamer);

        List<Comment> comments = commentRepository.findAllByPostOrderByCreatedDateAsc(post);
        Map<String, Gamer> owners =
                loadOwners(comments.stream().map(Comment::getOwner).toList());
        Map<String, String> avatars = avatarUrls.visibleTo(owners.values());

        List<CommentDto> dtos = comments.stream()
                .map(comment -> {
                    CommentDto dto = communityMapper.toDto(comment);
                    dto.setIsLiked(comment.getLikes().contains(gamer));
                    Gamer owner = owners.get(comment.getOwner());
                    if (owner != null) {
                        dto.setUsername(owner.getGamerUsername());
                        dto.setAvatar(avatars.get(owner.getUserId()));
                    }
                    return dto;
                })
                .toList();

        CommentsResponseBody body = new CommentsResponseBody();
        body.setComments(dtos);
        return respond(new CommentsResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberResponse getPostLikes(Gamer principal, String postId) {
        Gamer gamer = reload(principal);
        Post post = requirePost(postId);
        requireMember(post.getCommunity(), gamer);

        MemberResponseBody body = new MemberResponseBody();
        body.setMembers(toGamerDtos(post.getLikes(), post.getCommunity().getOwner()));
        return respond(new MemberResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberResponse getCommentLikes(Gamer principal, String commentId) {
        Gamer gamer = reload(principal);
        Comment comment = requireComment(commentId);
        Community community = comment.getPost().getCommunity();
        requireMember(community, gamer);

        MemberResponseBody body = new MemberResponseBody();
        body.setMembers(toGamerDtos(comment.getLikes(), community.getOwner()));
        return respond(new MemberResponse(), body);
    }

    // =======================================================================
    // Writes
    // =======================================================================

    @Override
    @Transactional
    public DefaultMessageResponse createCommunity(Gamer principal, CreateCommunityRequest request) {
        Gamer gamer = reload(principal);

        Community community = new Community();
        community.setCommunityId(UUID.randomUUID());
        community.setName(request.getName());
        community.setDescription(request.getDescription());
        community.setCommunityAvatar(request.getAvatar());
        community.setWallpaper(request.getWallpaper());
        community.setOwner(gamer);
        community.getMembers().add(gamer);
        communityRepository.save(community);

        return DefaultMessageResponse.of("Community created successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse createPost(Gamer principal, PostRequest request) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(request.getCommunityId());
        requireMember(community, gamer);

        Post post = new Post();
        post.setPostId(UUID.randomUUID());
        post.setOwner(gamer.getUserId());
        post.setTitle(request.getTitle());
        post.setBody(request.getBody());
        post.setPicture(request.getPicture());
        post.setCommunity(community);
        postRepository.save(post);

        // Everyone else in the community. This is the one fan-out in the app — a post in a
        // community of five hundred queues five hundred outbox rows — and it is what makes
        // a community feel inhabited rather than like a page you have to remember to visit.
        // The rows are small and the poller sends them in the background; the alternative,
        // a digest, is a scheduled job that tells people about a conversation after it has
        // finished.
        for (Gamer member : community.getMembers()) {
            notify(
                    gamer,
                    member,
                    String.format(Constants.COMMUNITY_POST_TITLE, community.getName()),
                    String.format(Constants.COMMUNITY_POST_BODY, gamer.getGamerUsername(), preview(post.getTitle())),
                    NotificationKind.COMMUNITY_POST,
                    post.getPostId().toString());
        }

        return DefaultMessageResponse.of("Post created successfully");
    }

    /**
     * Adds a comment.
     *
     * <p>This checked nothing at all: any authenticated user could comment on any post in
     * any community, including one they were not a member of.
     */
    @Override
    @Transactional
    public DefaultMessageResponse createComment(Gamer principal, CreateCommentRequest request) {
        Gamer gamer = reload(principal);
        Post post = requirePost(request.getPostId());
        requireMember(post.getCommunity(), gamer);

        Comment comment = new Comment();
        comment.setCommentId(UUID.randomUUID());
        comment.setMessage(request.getMessage());
        comment.setOwner(gamer.getUserId());
        // Only the owning side is set. `post.addComment(comment)` as well used to make
        // this a guaranteed HTTP 500: the id is assigned by hand, so Spring Data sees a
        // non-null id, treats the entity as detached and calls merge() — which returns a
        // *copy*. The original then reached the flush through the cascade on
        // Post.comments, and Hibernate found two instances claiming one identifier:
        // NonUniqueObjectException. No comment could be posted at all.
        comment.setPost(post);
        commentRepository.save(comment);

        notify(
                gamer,
                post.getOwner(),
                Constants.POST_COMMENT_TITLE,
                String.format(Constants.POST_COMMENT_BODY, gamer.getGamerUsername(), preview(request.getMessage())),
                NotificationKind.POST_COMMENT,
                post.getPostId().toString());

        return DefaultMessageResponse.of("Comment created successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse deleteCommunity(Gamer principal, CommunityRequest request) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(request.getCommunityId());
        if (!community.isOwnedBy(gamer) && !isAdmin(gamer)) {
            throw new BusinessException(TransactionCode.NOT_OWNER);
        }

        // Clearing the owning side removes every join-table row for this community.
        community.getMembers().clear();
        communityRepository.delete(community);

        return DefaultMessageResponse.of("Community deleted successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse deletePost(Gamer principal, String postId) {
        Gamer gamer = reload(principal);
        Post post = requirePost(postId);
        if (!post.getOwner().equals(gamer.getUserId()) && !isAdmin(gamer)) {
            throw new BusinessException(TransactionCode.NOT_OWNER);
        }

        // Detached from the community first so orphanRemoval takes the comments too.
        post.getCommunity().getPosts().remove(post);
        postRepository.delete(post);

        return DefaultMessageResponse.of("Post deleted successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse deleteComment(Gamer principal, String commentId) {
        Gamer gamer = reload(principal);
        Comment comment = requireComment(commentId);
        if (!comment.getOwner().equals(gamer.getUserId()) && !isAdmin(gamer)) {
            throw new BusinessException(TransactionCode.NOT_OWNER);
        }

        comment.getPost().getComments().remove(comment);
        commentRepository.delete(comment);

        return DefaultMessageResponse.of("Comment deleted successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse joinCommunity(Gamer principal, CommunityRequest request) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(request.getCommunityId());
        if (community.hasMember(gamer)) {
            throw new BusinessException(TransactionCode.ALREADY_MEMBER);
        }

        community.getMembers().add(gamer);
        communityRepository.save(community);

        return DefaultMessageResponse.of("Joined " + community.getName() + " successfully");
    }

    /**
     * Leaves a community, handing over ownership if the leaver owns it.
     *
     * <p>An owner used to be refused outright, which made ownership a trap: the only way
     * out was to delete the community and everyone else's posts with it. There was also
     * no succession at all, so an owner who stopped using the app — or deleted their
     * account — left the community permanently unadministrable.
     *
     * <p>Ownership passes to the longest-standing remaining member. A community with no
     * one left is deleted, because an empty community has nothing to administer.
     */
    @Override
    @Transactional
    public DefaultMessageResponse leaveCommunity(Gamer principal, CommunityRequest request) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(request.getCommunityId());
        if (!community.hasMember(gamer)) {
            throw new BusinessException(TransactionCode.NOT_MEMBER);
        }

        community.getMembers().remove(gamer);

        if (community.isOwnedBy(gamer)) {
            Optional<Gamer> successor = community.getMembers().stream()
                    .filter(Gamer::isEnabled)
                    .min(Comparator.comparing(Gamer::getUserId));

            if (successor.isEmpty()) {
                // Nobody is left to own it. orphanRemoval takes the posts and comments.
                communityRepository.delete(community);
                gamerRepository.save(gamer);
                return DefaultMessageResponse.of(
                        "Left " + community.getName() + "; it had no members left and was closed");
            }
            community.setOwner(successor.get());
            log.info(
                    "Ownership of {} passed to {}",
                    community.getCommunityId(),
                    successor.get().getUserId());
        }

        communityRepository.save(community);
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Left " + community.getName() + " successfully");
    }

    /**
     * Hands a community to another member.
     *
     * <p>Also the remedy when an owner's account is deleted: the row keeps a disabled
     * owner until an admin reassigns it here.
     */
    @Override
    @Transactional
    public DefaultMessageResponse transferOwnership(Gamer principal, String communityId, String newOwnerId) {
        Gamer gamer = reload(principal);
        Community community = requireCommunity(communityId);

        if (!community.isOwnedBy(gamer) && !isAdmin(gamer)) {
            throw new BusinessException(TransactionCode.NOT_OWNER);
        }

        Gamer newOwner = gamerRepository
                .findById(newOwnerId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
        if (!community.hasMember(newOwner)) {
            throw new BusinessException(TransactionCode.NOT_MEMBER);
        }
        if (!newOwner.isEnabled()) {
            throw new BusinessException(TransactionCode.ACCOUNT_DELETED);
        }

        community.setOwner(newOwner);
        communityRepository.save(community);
        return DefaultMessageResponse.of("Ownership transferred to " + newOwner.getGamerUsername());
    }

    // =======================================================================
    // Likes
    // =======================================================================

    @Override
    @Transactional
    public DefaultMessageResponse likePost(Gamer principal, String postId) {
        Gamer gamer = reload(principal);
        Post post = requirePost(postId);
        requireMember(post.getCommunity(), gamer);

        // Set.add reports whether it changed anything, so the check and the mutation
        // cannot disagree.
        if (!post.getLikes().add(gamer)) {
            throw new BusinessException(TransactionCode.ALREADY_LIKED);
        }
        postRepository.save(post);

        notify(
                gamer,
                post.getOwner(),
                Constants.POST_LIKE_TITLE,
                String.format(Constants.POST_LIKE_BODY, gamer.getGamerUsername()),
                NotificationKind.POST_LIKE,
                post.getPostId().toString());

        return DefaultMessageResponse.of("Liked post successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse unlikePost(Gamer principal, String postId) {
        Gamer gamer = reload(principal);
        Post post = requirePost(postId);
        requireMember(post.getCommunity(), gamer);

        post.getLikes().remove(gamer);
        postRepository.save(post);
        return DefaultMessageResponse.of("Unliked post successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse likeComment(Gamer principal, String commentId) {
        Gamer gamer = reload(principal);
        Comment comment = requireComment(commentId);
        requireMember(comment.getPost().getCommunity(), gamer);

        if (!comment.getLikes().add(gamer)) {
            throw new BusinessException(TransactionCode.ALREADY_LIKED);
        }
        commentRepository.save(comment);

        notify(
                gamer,
                comment.getOwner(),
                Constants.COMMENT_LIKE_TITLE,
                String.format(Constants.COMMENT_LIKE_BODY, gamer.getGamerUsername()),
                NotificationKind.COMMENT_LIKE,
                // The post, not the comment: a comment has no screen of its own, and the
                // post is where you go to read it in context.
                comment.getPost().getPostId().toString());

        return DefaultMessageResponse.of("Liked comment successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse unlikeComment(Gamer principal, String commentId) {
        Gamer gamer = reload(principal);
        Comment comment = requireComment(commentId);
        requireMember(comment.getPost().getCommunity(), gamer);

        comment.getLikes().remove(gamer);
        commentRepository.save(comment);
        return DefaultMessageResponse.of("Unliked comment successfully");
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Queues a notification, unless there is nobody to tell.
     *
     * <p>Three things are skipped here rather than at each of the five call sites, which
     * is the point of having it: notifying yourself about your own like, notifying an
     * account with no device registered, and notifying somebody who has blocked the actor
     * or been blocked by them. The last one matters — a block that still lets the blocked
     * person put a notification on your phone is not a block.
     */
    private void notify(
            Gamer actor, String recipientId, String title, String body, NotificationKind kind, String targetId) {
        if (recipientId == null || recipientId.equals(actor.getUserId())) {
            return;
        }
        gamerRepository
                .findById(recipientId)
                .ifPresent(recipient -> notify(actor, recipient, title, body, kind, targetId));
    }

    private void notify(
            Gamer actor, Gamer recipient, String title, String body, NotificationKind kind, String targetId) {
        if (recipient == null
                || recipient.getUserId().equals(actor.getUserId())
                || recipient.getFcmToken() == null
                || recipient.getDeletedAt() != null
                || actor.hasBlockRelationshipWith(recipient)) {
            return;
        }
        events.publishEvent(
                new NotificationRequestedEvent(recipient.getUserId(), recipient.getFcmToken(), title, body, kind, targetId));
    }

    /**
     * A short lead-in for a notification body.
     *
     * <p>Truncated here rather than left to the outbox column limit: a body cut off at a
     * thousand characters is still a wall of text on a lock screen.
     */
    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 59) + "…";
    }

    /** Re-reads the principal inside this transaction; the filter's copy is detached. */
    private Gamer reload(Gamer principal) {
        return gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    private Community requireCommunity(String communityId) {
        return communityRepository
                .findById(Ids.uuid(communityId))
                .orElseThrow(() -> new BusinessException(TransactionCode.COMMUNITY_NOT_FOUND));
    }

    private Post requirePost(String postId) {
        return postRepository
                .findById(Ids.uuid(postId))
                .orElseThrow(() -> new BusinessException(TransactionCode.POST_NOT_FOUND));
    }

    private Comment requireComment(String commentId) {
        return commentRepository
                .findById(Ids.uuid(commentId))
                .orElseThrow(() -> new BusinessException(TransactionCode.COMMENT_NOT_FOUND));
    }

    /** Community content is for members. Admins get through so they can moderate. */
    private void requireMember(Community community, Gamer gamer) {
        if (!community.hasMember(gamer) && !isAdmin(gamer)) {
            throw new BusinessException(TransactionCode.NOT_MEMBER);
        }
    }

    private boolean isAdmin(Gamer gamer) {
        return gamer.getRole() == Role.ADMIN;
    }

    private Map<String, Gamer> loadOwners(Collection<String> ownerIds) {
        Set<String> ids = new HashSet<>(ownerIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return gamerRepository.findAllById(ids).stream().collect(Collectors.toMap(Gamer::getUserId, g -> g));
    }

    private List<GamerDto> toGamerDtos(Collection<Gamer> gamers, Gamer owner) {
        Map<String, String> avatars = avatarUrls.visibleTo(gamers);
        return gamers.stream()
                .map(g -> {
                    GamerDto dto = communityMapper.toDto(g);
                    dto.setAvatar(avatars.get(g.getUserId()));
                    dto.setFrame(cosmeticUrls.frameUrl(g));
                    dto.setIsOwner(owner != null && owner.getUserId().equals(g.getUserId()));
                    return dto;
                })
                .toList();
    }

    private PostResponse postResponse(List<Post> posts, Gamer gamer) {
        Map<String, Gamer> owners =
                loadOwners(posts.stream().map(Post::getOwner).toList());
        Map<String, String> avatars = avatarUrls.visibleTo(owners.values());

        List<PostDto> dtos = posts.stream()
                .map(post -> {
                    PostDto dto = communityMapper.toDto(post);
                    Gamer owner = owners.get(post.getOwner());
                    if (owner != null) {
                        dto.setUsername(owner.getGamerUsername());
                        dto.setAvatar(avatars.get(owner.getUserId()));
                    }
                    dto.setIsLiked(post.getLikes().contains(gamer));
                    return dto;
                })
                .toList();

        PostResponseBody body = new PostResponseBody();
        body.setPosts(dtos);
        return respond(new PostResponse(), body);
    }

    private <B extends BaseModel, R extends BaseResponse<B>> R respond(R response, B body) {
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
