package com.gamebuddy.community.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.community.application.mapper.CommunityMapper;
import com.gamebuddy.community.application.mapper.CommunityMapperImpl;
import com.gamebuddy.community.infrastructure.entity.*;
import com.gamebuddy.community.infrastructure.repository.*;
import com.gamebuddy.community.interfaces.request.CommunityRequest;
import com.gamebuddy.community.interfaces.request.CreateCommentRequest;
import com.gamebuddy.community.interfaces.request.CreateCommunityRequest;
import com.gamebuddy.community.interfaces.request.PostRequest;
import com.gamebuddy.community.interfaces.response.*;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultCommunityServiceTest {

    /**
     * The real filter, not a mock: it is a pure function over a word list, so a stub would
     * only prove a stub was called. What matters is whether a slur actually gets stored.
     */
    @Spy
    private TextModerationService textModeration = new TextModerationService();

    @InjectMocks
    private DefaultCommunityService communityService;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AvatarsRepository avatarsRepository;

    // The one place that decides which picture a gamer shows. Mocked rather than
    // real because it reaches object storage, which these tests have no business
    // standing up.
    @Mock
    private AvatarUrls avatarUrls;

    @Mock
    private CosmeticUrls cosmeticUrls;

    @Spy
    private CommunityMapper communityMapper = new CommunityMapperImpl();

    private static final Pageable PAGE = PageRequest.of(0, 20);

    private Gamer member;
    private Gamer outsider;
    private Community community;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        member = gamer("member@example.com", "member");
        outsider = gamer("outsider@example.com", "outsider");

        community = new Community();
        community.setCommunityId(UUID.randomUUID());
        community.setName("Valorant TR");
        community.setDescription("desc");
        community.setOwner(member);
        community.getMembers().add(member);
        community.getMembers().add(member);

        post = new Post();
        post.setPostId(UUID.randomUUID());
        post.setOwner(member.getUserId());
        post.setTitle("title");
        post.setBody("body");
        post.setUpdatedDate(Instant.now());
        post.setCommunity(community);
        community.getPosts().add(post);

        comment = new Comment();
        comment.setCommentId(UUID.randomUUID());
        comment.setOwner(member.getUserId());
        comment.setMessage("nice");
        comment.setCreatedDate(Instant.now());
        comment.setPost(post);
        post.getComments().add(comment);

        when(gamerRepository.findById(member.getUserId())).thenReturn(Optional.of(member));
        when(gamerRepository.findById(outsider.getUserId())).thenReturn(Optional.of(outsider));
        when(communityRepository.findById(community.getCommunityId())).thenReturn(Optional.of(community));
        when(postRepository.findById(post.getPostId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(comment.getCommentId())).thenReturn(Optional.of(comment));
        when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(member));
    }

    private static Gamer gamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        return g;
    }

    private CommunityRequest communityRequest() {
        CommunityRequest r = new CommunityRequest();
        r.setCommunityId(community.getCommunityId().toString());
        return r;
    }

    // =====================================================================
    // The authorisation gap this service was built around
    // =====================================================================

    @Nested
    @DisplayName("community content is only readable by members")
    class MembershipEnforcement {

        @Test
        void testGetMembers_whenNotAMember_ReturnErrorCode132() {
            String id = community.getCommunityId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.getMembers(outsider, id));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testGetPostComments_whenNotAMember_ReturnErrorCode132() {
            String id = post.getPostId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.getPostComments(outsider, id));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testGetPostLikes_whenNotAMember_ReturnErrorCode132() {
            String id = post.getPostId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.getPostLikes(outsider, id));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testGetCommentLikes_whenNotAMember_ReturnErrorCode132() {
            String id = comment.getCommentId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.getCommentLikes(outsider, id));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testCreateComment_whenNotAMember_ReturnErrorCode132() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(post.getPostId().toString());
            request.setMessage("hello");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.createComment(outsider, request));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testLikePost_whenNotAMember_ReturnErrorCode132() {
            String id = post.getPostId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> communityService.likePost(outsider, id));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testLikeComment_whenNotAMember_ReturnErrorCode132() {
            String id = comment.getCommentId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.likeComment(outsider, id));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a non-member gets 403 rather than an empty list that looks like an empty community")
        void testGetCommunitiesPosts_whenNotAMember_ReturnErrorCode132() {
            String id = community.getCommunityId().toString();

            BusinessException ex = assertThrows(
                    BusinessException.class, () -> communityService.getCommunitiesPosts(outsider, id, PAGE));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("an admin passes the membership check so they can moderate")
        void testGetMembers_whenAdmin_Succeeds() {
            outsider.setRole(Role.ADMIN);

            MemberResponse response = communityService.getMembers(
                    outsider, community.getCommunityId().toString());

            assertEquals("100", response.getStatus().getCode());
        }
    }

    // =====================================================================

    @Nested
    class Reads {

        @Test
        void testGetCommunities_whenCalled_ReturnListWithJoinedFlag() {
            when(communityRepository.findAll()).thenReturn(List.of(community));

            CommunityResponse response = communityService.getCommunities(member);
            var dto = response.getBody().getData().getCommunities().get(0);

            assertEquals("100", response.getStatus().getCode());
            assertEquals("Valorant TR", dto.getName());
            assertTrue(dto.getIsJoined());
            assertEquals(1, dto.getMemberCount());
            assertEquals(1, dto.getPostCount());
        }

        @Test
        void testGetCommunities_whenNotJoined_IsJoinedIsFalse() {
            when(communityRepository.findAll()).thenReturn(List.of(community));

            CommunityResponse response = communityService.getCommunities(outsider);

            assertFalse(response.getBody().getData().getCommunities().get(0).getIsJoined());
        }

        @Test
        void testGetMembers_whenCommunityNotFound_ReturnErrorCode131() {
            String id = UUID.randomUUID().toString();
            when(communityRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> communityService.getMembers(member, id));
            assertEquals(131, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a malformed community id is a 400, not the 500 UUID.fromString produced")
        void testGetMembers_whenIdMalformed_ReturnErrorCode148() {
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.getMembers(member, "not-a-uuid"));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        void testGetMembers_whenMember_ReturnMembersWithOwnerFlag() {
            MemberResponse response = communityService.getMembers(
                    member, community.getCommunityId().toString());
            var dto = response.getBody().getData().getMembers().get(0);

            assertEquals("member", dto.getGamerUsername());
            assertTrue(dto.getIsOwner());
        }

        @Test
        @DisplayName("a member who never chose an avatar does not blow up the member list")
        void testGetMembers_whenAvatarIsNull_ReturnsNullAvatar() {
            MemberResponse response = communityService.getMembers(
                    member, community.getCommunityId().toString());

            assertNull(response.getBody().getData().getMembers().get(0).getAvatar());
            // findById(null) would have raised InvalidDataAccessApiUsageException.
            verify(avatarsRepository, never()).findById(any());
        }

        @Test
        void testGetCommunitiesPosts_whenMember_ReturnPosts() {
            when(postRepository.findAllByCommunityOrderByUpdatedDateDesc(community, PAGE))
                    .thenReturn(List.of(post));

            PostResponse response = communityService.getCommunitiesPosts(
                    member, community.getCommunityId().toString(), PAGE);
            var dto = response.getBody().getData().getPosts().get(0);

            assertEquals("title", dto.getTitle());
            assertEquals("member", dto.getUsername());
            assertEquals("Valorant TR", dto.getCommunityName());
            assertEquals(1, dto.getCommentCount());
            assertFalse(dto.getIsLiked());
        }

        @Test
        @DisplayName("the home feed is ordered and paged by the database, not in memory")
        void testGetJoinedCommunitiesPosts_whenCalled_DelegatesPagingToTheRepository() {
            // Membership is read from the owning side now, not from Gamer.
            when(communityRepository.findAllByMembersContaining(member)).thenReturn(List.of(community));
            when(postRepository.findAllByCommunityInOrderByUpdatedDateDesc(anyCollection(), eq(PAGE)))
                    .thenReturn(List.of(post));

            PostResponse response = communityService.getJoinedCommunitiesPosts(member, PAGE);

            assertEquals(1, response.getBody().getData().getPosts().size());
            verify(postRepository).findAllByCommunityInOrderByUpdatedDateDesc(anyCollection(), eq(PAGE));
        }

        @Test
        void testGetJoinedCommunitiesPosts_whenNoCommunities_ReturnEmpty() {
            PostResponse response = communityService.getJoinedCommunitiesPosts(outsider, PAGE);

            assertTrue(response.getBody().getData().getPosts().isEmpty());
            verify(postRepository, never()).findAllByCommunityInOrderByUpdatedDateDesc(anyCollection(), any());
        }

        @Test
        void testGetPostComments_whenMember_ReturnComments() {
            when(commentRepository.findAllByPostOrderByCreatedDateAsc(post)).thenReturn(List.of(comment));

            CommentsResponse response =
                    communityService.getPostComments(member, post.getPostId().toString());
            var dto = response.getBody().getData().getComments().get(0);

            assertEquals("nice", dto.getMessage());
            assertEquals("member", dto.getUsername());
        }

        @Test
        void testGetPostLikes_whenMember_ReturnLikes() {
            post.getLikes().add(member);

            MemberResponse response =
                    communityService.getPostLikes(member, post.getPostId().toString());

            assertEquals(1, response.getBody().getData().getMembers().size());
        }

        @Test
        void testGetCommentLikes_whenMember_ReturnLikes() {
            comment.getLikes().add(member);

            MemberResponse response = communityService.getCommentLikes(
                    member, comment.getCommentId().toString());

            assertEquals(1, response.getBody().getData().getMembers().size());
        }
    }

    @Nested
    class Writes {

        @Test
        void testCreateCommunity_whenCalled_MakesTheCreatorOwnerAndMember() {
            CreateCommunityRequest request = new CreateCommunityRequest();
            request.setName("New");
            request.setDescription("desc");

            DefaultMessageResponse response = communityService.createCommunity(member, request);

            assertEquals("100", response.getStatus().getCode());
            verify(communityRepository).save(argThat(c -> c.isOwnedBy(member) && c.hasMember(member)));
        }

        @Test
        void testCreatePost_whenCommunityNotFound_ReturnErrorCode131() {
            when(communityRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
            PostRequest request = new PostRequest();
            request.setCommunityId(UUID.randomUUID().toString());
            request.setTitle("t");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.createPost(member, request));
            assertEquals(131, ex.getTransactionCode().getId());
        }

        @Test
        void testCreatePost_whenNotAMember_ReturnErrorCode132() {
            PostRequest request = new PostRequest();
            request.setCommunityId(community.getCommunityId().toString());
            request.setTitle("t");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.createPost(outsider, request));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        void testCreatePost_whenMember_ReturnSuccess() {
            PostRequest request = new PostRequest();
            request.setCommunityId(community.getCommunityId().toString());
            request.setTitle("t");
            request.setBody("b");

            DefaultMessageResponse response = communityService.createPost(member, request);

            assertEquals("100", response.getStatus().getCode());
            verify(postRepository).save(argThat(p -> p.getCommunity().equals(community)));
        }

        @Test
        void testCreateComment_whenPostNotFound_ReturnErrorCode133() {
            when(postRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(UUID.randomUUID().toString());
            request.setMessage("hi");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.createComment(member, request));
            assertEquals(133, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a new comment is linked to its post, so its community is reachable")
        void testCreateComment_whenMember_LinksCommentToPost() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(post.getPostId().toString());
            request.setMessage("hi");

            communityService.createComment(member, request);

            verify(commentRepository).save(argThat(c -> post.equals(c.getPost())));
        }
    }

    @Nested
    class Deletes {

        @Test
        void testDeleteCommunity_whenNotOwner_ReturnErrorCode134() {
            CommunityRequest request = communityRequest();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.deleteCommunity(outsider, request));
            assertEquals(134, ex.getTransactionCode().getId());
        }

        @Test
        void testDeleteCommunity_whenAdmin_Succeeds() {
            outsider.setRole(Role.ADMIN);

            assertEquals(
                    "100",
                    communityService
                            .deleteCommunity(outsider, communityRequest())
                            .getStatus()
                            .getCode());
            verify(communityRepository).delete(community);
        }

        @Test
        @DisplayName("deleting a community detaches its members so their profile screen does not break")
        void testDeleteCommunity_whenOwner_DetachesMembers() {
            communityService.deleteCommunity(member, communityRequest());

            assertFalse(community.getMembers().contains(member));
            verify(communityRepository).delete(community);
        }

        @Test
        void testDeletePost_whenNotOwner_ReturnErrorCode134() {
            String id = post.getPostId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.deletePost(outsider, id));
            assertEquals(134, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("deleting a post detaches it from the community so its comments cascade away")
        void testDeletePost_whenOwner_DetachesFromCommunity() {
            communityService.deletePost(member, post.getPostId().toString());

            assertFalse(community.getPosts().contains(post));
            verify(postRepository).delete(post);
        }

        @Test
        void testDeleteComment_whenNotOwner_ReturnErrorCode134() {
            String id = comment.getCommentId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.deleteComment(outsider, id));
            assertEquals(134, ex.getTransactionCode().getId());
        }

        @Test
        void testDeleteComment_whenOwner_DetachesFromPost() {
            communityService.deleteComment(member, comment.getCommentId().toString());

            assertFalse(post.getComments().contains(comment));
            verify(commentRepository).delete(comment);
        }
    }

    @Nested
    class Membership {

        @Test
        void testJoinCommunity_whenAlreadyMember_ReturnErrorCode136() {
            CommunityRequest request = communityRequest();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.joinCommunity(member, request));
            assertEquals(136, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("joining writes the one owning side, not both")
        void testJoinCommunity_whenNotMember_AddsToTheOwningSide() {
            communityService.joinCommunity(outsider, communityRequest());

            assertTrue(community.hasMember(outsider));
            assertTrue(community.getMembers().contains(outsider));
            verify(communityRepository).save(community);
            // Gamer no longer carries a second mapping of community_members_join. Writing
            // both sides issued two inserts of the same row, because neither declared
            // mappedBy and Hibernate saw two unrelated relationships.
            verify(gamerRepository, never()).save(outsider);
        }

        @Test
        void testLeaveCommunity_whenNotMember_ReturnErrorCode132() {
            CommunityRequest request = communityRequest();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.leaveCommunity(outsider, request));
            assertEquals(132, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("an owner who leaves hands the community to the remaining member")
        void testLeaveCommunity_whenOwnerAndOthersRemain_TransfersOwnership() {
            community.getMembers().add(outsider);
            community.getMembers().add(outsider);

            communityService.leaveCommunity(member, communityRequest());

            // Owners used to be refused outright, so the only way out was deleting the
            // community and everyone else's posts with it.
            assertFalse(community.hasMember(member));
            assertTrue(community.isOwnedBy(outsider));
            verify(communityRepository, never()).delete(any());
        }

        @Test
        @DisplayName("the last member leaving closes the community rather than orphaning it")
        void testLeaveCommunity_whenOwnerIsLastMember_DeletesTheCommunity() {
            communityService.leaveCommunity(member, communityRequest());

            verify(communityRepository).delete(community);
        }

        @Test
        @DisplayName("a deleted account is never made the successor")
        void testLeaveCommunity_whenOnlyRemainingMemberIsDeleted_DeletesTheCommunity() {
            outsider.setDeletedAt(java.time.Instant.now());
            community.getMembers().add(outsider);

            communityService.leaveCommunity(member, communityRequest());

            verify(communityRepository).delete(community);
        }

        @Test
        void testLeaveCommunity_whenMember_UnlinksBothSides() {
            community.getMembers().add(outsider);
            community.getMembers().add(outsider);

            communityService.leaveCommunity(outsider, communityRequest());

            assertFalse(community.hasMember(outsider));
            assertFalse(community.getMembers().contains(outsider));
        }
    }

    @Nested
    class Likes {

        @Test
        void testLikePost_whenAlreadyLiked_ReturnErrorCode139() {
            post.getLikes().add(member);
            String id = post.getPostId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> communityService.likePost(member, id));
            assertEquals(139, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("the like count is derived from the like set, so the two cannot disagree")
        void testLikePost_whenMember_CountMatchesTheSet() {
            communityService.likePost(member, post.getPostId().toString());

            assertTrue(post.getLikes().contains(member));
            assertEquals(post.getLikes().size(), post.getLikeCount());
        }

        @Test
        void testUnlikePost_whenCalled_RemovesTheLike() {
            post.getLikes().add(member);

            communityService.unlikePost(member, post.getPostId().toString());

            assertFalse(post.getLikes().contains(member));
            assertEquals(0, post.getLikeCount());
        }

        @Test
        void testLikeComment_whenAlreadyLiked_ReturnErrorCode139() {
            comment.getLikes().add(member);
            String id = comment.getCommentId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> communityService.likeComment(member, id));
            assertEquals(139, ex.getTransactionCode().getId());
        }

        @Test
        void testLikeComment_whenMember_CountMatchesTheSet() {
            communityService.likeComment(member, comment.getCommentId().toString());

            assertTrue(comment.getLikes().contains(member));
            assertEquals(1, comment.getLikeCount());
        }

        @Test
        void testUnlikeComment_whenCalled_RemovesTheLike() {
            comment.getLikes().add(member);

            communityService.unlikeComment(member, comment.getCommentId().toString());

            assertFalse(comment.getLikes().contains(member));
        }
    }
}
