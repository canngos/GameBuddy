package com.gamebuddy.profile.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.profile.application.mapper.*;
import com.gamebuddy.profile.interfaces.request.FriendRequest;
import com.gamebuddy.profile.interfaces.response.*;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultProfileServiceTest {

    @InjectMocks
    private DefaultProfileService profileService;

    @Mock
    private KeywordsRepository keywordsRepository;

    @Mock
    private GamesRepository gamesRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private AvatarsRepository avatarsRepository;

    // The one place that decides which picture a gamer shows. Mocked rather than
    // real because it reaches object storage, which these tests have no business
    // standing up.
    @Mock
    private AvatarUrls avatarUrls;

    @Mock
    private CosmeticUrls cosmeticUrls;

    @Mock
    private BadgeService badges;

    @Mock
    private ApplicationEventPublisher events;

    // Real generated mappers: mapping is logic worth exercising, and a mock would
    // return null and prove nothing.
    @Spy
    private ProfileCatalogueMapper profileCatalogueMapper = new ProfileCatalogueMapperImpl();

    @Spy
    private ProfileMapper profileMapper = new ProfileMapperImpl();

    private Gamer gamer;
    private Gamer other;

    @BeforeEach
    void setUp() {
        gamer = newGamer("me@example.com", "me");
        other = newGamer("other@example.com", "other");

        when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
        when(gamerRepository.findById(other.getUserId())).thenReturn(Optional.of(other));
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        g.setAge(25);
        g.setCountry("TR");
        g.setGender("M");
        g.setCoin(100);
        g.setFcmToken("fcm-" + username);
        return g;
    }

    private static Avatars avatar() {
        Avatars a = new Avatars();
        a.setId(UUID.randomUUID());
        a.setImage("img.png");
        return a;
    }

    private static Games game(String name) {
        Games g = new Games();
        g.setGameId("g1");
        g.setGameName(name);
        g.setCategory("FPS");
        g.setAvgVote(4.5f);
        return g;
    }

    /** Friendship is the tier above matching, so the pair must have matched first. */
    private static void matched(Gamer a, Gamer b) {
        a.getApprovedMatches().add(b);
        b.getApprovedMatches().add(a);
    }

    private static FriendRequest request(Gamer target) {
        FriendRequest r = new FriendRequest();
        r.setUserId(target.getUserId());
        return r;
    }

    // =====================================================================

    @Nested
    class GetUserInfo {

        @Test
        void testGetUserInfo_whenUserNotFound_ReturnErrorCode103() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.empty());
            String id = gamer.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.getUserInfo(gamer, id));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testGetUserInfo_whenOwnProfile_ReturnsPrivateFields() {
            // visibleToOwner, not visibleTo: someone looking at their own profile sees
            // their own upload even while it is waiting on a moderator.
            when(avatarUrls.visibleToOwner(gamer)).thenReturn("img.png");

            UserInfoResponse response = profileService.getUserInfo(gamer, gamer.getUserId());
            var body = response.getBody().getData();

            assertEquals("100", response.getStatus().getCode());
            assertEquals("me@example.com", body.getEmail());
            assertEquals(100, body.getCoin());
            assertNotNull(body.getFriends());
            assertEquals("img.png", body.getAvatar());
        }

        @Test
        @DisplayName("another gamer's profile no longer discloses their email, coins or friend list")
        void testGetUserInfo_whenOtherProfile_HidesPrivateFields() {
            UserInfoResponse response = profileService.getUserInfo(gamer, other.getUserId());
            var body = response.getBody().getData();

            // The endpoint used to return the whole row for any id the caller asked
            // for, so any account could harvest every user's email address.
            assertNull(body.getEmail(), "email must not leak to another user");
            assertNull(body.getCoin(), "coin balance must not leak to another user");
            assertNull(body.getFriends(), "friend list must not leak to another user");
            assertEquals("other", body.getUsername());
        }

        @Test
        @DisplayName("a gamer who blocked you is indistinguishable from one who does not exist")
        void testGetUserInfo_whenBlockedByTarget_ReturnErrorCode103() {
            other.getBlockedFriends().add(gamer);
            String id = other.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.getUserInfo(gamer, id));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a gamer with no avatar does not blow up the profile")
        void testGetUserInfo_whenAvatarIsNull_ReturnsNullAvatar() {
            gamer.setAvatar(null);

            UserInfoResponse response = profileService.getUserInfo(gamer, gamer.getUserId());

            assertNull(response.getBody().getData().getAvatar());
            // findById(null) would have raised InvalidDataAccessApiUsageException.
            verify(avatarsRepository, never()).findById(any());
        }
    }

    @Nested
    class Catalogue {

        @Test
        void testGetKeywords_whenCalled_ReturnKeywords() {
            Keywords k = new Keywords();
            k.setId(UUID.randomUUID());
            k.setKeywordName("Competitive");
            when(keywordsRepository.findAll()).thenReturn(List.of(k));

            KeywordsResponse response = profileService.getKeywords();

            assertEquals("100", response.getStatus().getCode());
            assertEquals(
                    "Competitive",
                    response.getBody().getData().getKeywords().get(0).getKeywordName());
        }

        @Test
        void testGetGames_whenCalled_ReturnGames() {
            when(gamesRepository.findAll()).thenReturn(List.of(game("Valorant")));

            GamesResponse response = profileService.getGames();

            assertEquals(1, response.getBody().getData().getGames().size());
            assertEquals(
                    "Valorant", response.getBody().getData().getGames().get(0).getGameName());
        }

        @Test
        void testGetPopularGames_whenCalled_ReturnPopularGames() {
            when(gamesRepository.findAllByIsPopularTrueOrderByAvgVoteDesc()).thenReturn(List.of(game("CS")));

            assertEquals(
                    1,
                    profileService
                            .getPopularGames()
                            .getBody()
                            .getData()
                            .getGames()
                            .size());
        }

        @Test
        @DisplayName("an unknown game is a 404, not the 500 that DB_ERROR produced")
        void testGetGame_whenNotFound_ReturnErrorCode151() {
            when(gamesRepository.findById("nope")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.getGame("nope"));
            assertEquals(151, ex.getTransactionCode().getId());
        }

        @Test
        void testGetGame_whenFound_ReturnGame() {
            when(gamesRepository.findById("g1")).thenReturn(Optional.of(game("Valorant")));

            assertEquals(
                    "Valorant",
                    profileService
                            .getGame("g1")
                            .getBody()
                            .getData()
                            .getGameData()
                            .getGameName());
        }

        @Test
        @DisplayName("the whole stock catalogue, the same for everyone")
        void testGetAvatars_whenCalled_ReturnEveryCatalogueAvatar() {
            when(avatarsRepository.findAll()).thenReturn(new ArrayList<>(List.of(avatar(), avatar())));

            AvatarsResponse response = profileService.getAvatars();

            assertEquals(2, response.getBody().getData().getAvatars().size());
        }

        @Test
        @DisplayName("object keys come back resolved to URLs")
        void testGetAvatars_whenCalled_ResolvesImageKeys() {
            Avatars stock = avatar();
            stock.setImage("default-avatars/avatar-01.png");
            when(avatarsRepository.findAll()).thenReturn(new ArrayList<>(List.of(stock)));
            when(avatarUrls.publicUrlFor("default-avatars/avatar-01.png")).thenReturn("https://cdn/avatar-01.png");

            AvatarsResponse response = profileService.getAvatars();

            assertEquals(
                    "https://cdn/avatar-01.png",
                    response.getBody().getData().getAvatars().get(0).getImage());
        }

        // The marketplace test went with the marketplace. What is on sale now is frames and
        // banners; see DefaultCosmeticServiceTest.
    }

    // The Achievement cases went with achievements. Badges replaced them: the catalogue
    // is an enum, the rules are evaluated in one place, and both are covered by
    // DefaultBadgeServiceTest.

    // The BuyItem cases moved with buying itself, to DefaultCosmeticServiceTest — including
    // the two regression tests worth keeping: that an achievement threshold crossed by more
    // than one purchase still fires, and that an already-earned one does not notify twice.

    @Nested
    class Friends {

        @Test
        void testGetFriends_whenCalled_ReturnListOfFriends() {
            gamer.getFriends().add(other);

            FriendsResponse response = profileService.getFriends(gamer);

            assertEquals(1, response.getBody().getData().getFriends().size());
            assertEquals(
                    "other", response.getBody().getData().getFriends().get(0).getUsername());
        }

        @Test
        @DisplayName("sent requests are read from the join table, not off the entity")
        void testGetSentFriendRequests_whenCalled_ReturnsWhoWasAsked() {
            // Gamer.waitingFriends holds requests *received*. The outgoing direction is the
            // inverse of that mapping and is not navigable from the sender at all, so
            // walking it would quietly return nothing — which is why this goes through a
            // query rather than the entity.
            when(gamerRepository.findRequestedByMe(gamer.getUserId())).thenReturn(List.of(other));

            FriendsResponse response = profileService.getSentFriendRequests(gamer);

            assertEquals(1, response.getBody().getData().getFriends().size());
            assertEquals(
                    "other", response.getBody().getData().getFriends().get(0).getUsername());
        }

        @Test
        @DisplayName("reading the friends list writes nothing")
        void testGetFriends_whenCalled_WritesNothing() {
            gamer.getFriends().add(other);

            profileService.getFriends(gamer);

            // It used to award an achievement here, so a gamer with friends who never
            // opened this screen never earned it — and opening it repeatedly saved the row
            // for no reason. It is a read.
            verify(gamerRepository, never()).save(any());
        }

        @Test
        @DisplayName("friend avatars are resolved in one batch, not one lookup per friend")
        void testGetFriends_whenManyFriends_ResolvesAvatarsInOneQuery() {
            for (int i = 0; i < 10; i++) {
                Gamer friend = newGamer("f" + i + "@example.com", "f" + i);
                friend.setAvatar(UUID.randomUUID());
                gamer.getFriends().add(friend);
            }
            when(avatarUrls.visibleTo(anyCollection())).thenReturn(Map.of());

            profileService.getFriends(gamer);

            // The batching now lives in AvatarUrls — see AvatarUrlsTest for the query
            // count. What matters here is that this service asks once for the whole
            // collection rather than once per friend, which is what turned a friends
            // list into N+1 round trips.
            verify(avatarUrls, times(1)).visibleTo(anyCollection());
            verify(avatarUrls, never()).visibleTo(any(Gamer.class));
        }

        @Test
        void testGetWaitingFriends_whenCalled_ReturnRequests() {
            gamer.getWaitingFriends().add(other);

            assertEquals(
                    1,
                    profileService
                            .getWaitingFriends(gamer)
                            .getBody()
                            .getData()
                            .getFriends()
                            .size());
        }

        @Test
        void testGetBlockedFriends_whenCalled_ReturnBlocked() {
            gamer.getBlockedFriends().add(other);

            assertEquals(
                    1,
                    profileService
                            .getBlockedFriends(gamer)
                            .getBody()
                            .getData()
                            .getFriends()
                            .size());
        }

        @Test
        void testAcceptFriend_whenTargetNotFound_ReturnErrorCode103() {
            when(gamerRepository.findById(other.getUserId())).thenReturn(Optional.empty());
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.acceptFriend(gamer, req));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptFriend_whenAlreadyFriends_ReturnErrorCode115() {
            gamer.getFriends().add(other);
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.acceptFriend(gamer, req));
            assertEquals(115, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptFriend_whenNoPendingRequest_ReturnErrorCode116() {
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.acceptFriend(gamer, req));
            assertEquals(116, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptFriend_whenValid_LinksBothSidesAndNotifies() {
            gamer.getWaitingFriends().add(other);

            DefaultMessageResponse response = profileService.acceptFriend(gamer, request(other));

            assertEquals("100", response.getStatus().getCode());
            assertTrue(gamer.getFriends().contains(other));
            assertTrue(other.getFriends().contains(gamer), "the other side must be linked too");
            assertFalse(gamer.getWaitingFriends().contains(other));
            verify(gamerRepository).save(other);

            ArgumentCaptor<NotificationRequestedEvent> captor =
                    ArgumentCaptor.forClass(NotificationRequestedEvent.class);
            verify(events, atLeastOnce()).publishEvent(captor.capture());
            assertTrue(captor.getAllValues().stream()
                    .anyMatch(e -> "fcm-other".equals(e.fcmToken())
                            && e.title().equals(com.gamebuddy.common.util.Constants.FRIEND_REQUEST_ACCEPTED_TITLE)));
        }

        @Test
        void testRejectFriend_whenNoPendingRequest_ReturnErrorCode116() {
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.rejectFriend(gamer, req));
            assertEquals(116, ex.getTransactionCode().getId());
        }

        @Test
        void testRejectFriend_whenValid_DropsTheRequest() {
            gamer.getWaitingFriends().add(other);

            assertEquals(
                    "100",
                    profileService
                            .rejectFriend(gamer, request(other))
                            .getStatus()
                            .getCode());
            assertFalse(gamer.getWaitingFriends().contains(other));
        }

        @Test
        @DisplayName("withdrawing takes the request off the recipient's list, not the sender's")
        void testWithdrawFriendRequest_whenPending_DropsIt() {
            other.getWaitingFriends().add(gamer);

            assertEquals(
                    "100",
                    profileService
                            .withdrawFriendRequest(gamer, request(other))
                            .getStatus()
                            .getCode());
            assertFalse(other.getWaitingFriends().contains(gamer));
            verify(gamerRepository).save(other);
        }

        @Test
        void testWithdrawFriendRequest_whenNothingPending_ReturnErrorCode116() {
            FriendRequest req = request(other);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.withdrawFriendRequest(gamer, req));
            assertEquals(116, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a request you received is not yours to withdraw — reject is that door")
        void testWithdrawFriendRequest_whenTheyAskedYou_ReturnErrorCode116() {
            gamer.getWaitingFriends().add(other);
            FriendRequest req = request(other);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.withdrawFriendRequest(gamer, req));
            assertEquals(116, ex.getTransactionCode().getId());
            assertTrue(gamer.getWaitingFriends().contains(other));
        }

        @Test
        void testRemoveFriend_whenNotFriends_ReturnErrorCode117() {
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.removeFriend(gamer, req));
            assertEquals(117, ex.getTransactionCode().getId());
        }

        @Test
        void testRemoveFriend_whenValid_UnlinksBothSides() {
            gamer.getFriends().add(other);
            other.getFriends().add(gamer);

            profileService.removeFriend(gamer, request(other));

            assertFalse(gamer.getFriends().contains(other));
            assertFalse(other.getFriends().contains(gamer));
            verify(gamerRepository).save(other);
        }
    }

    @Nested
    class BlockAndRequest {

        @Test
        void testBlockUser_whenAlreadyBlocked_ReturnErrorCode118() {
            gamer.getBlockedFriends().add(other);
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.blockUser(gamer, req));
            assertEquals(118, ex.getTransactionCode().getId());
        }

        @Test
        void testBlockUser_whenBlockingSelf_ReturnErrorCode148() {
            FriendRequest req = request(gamer);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.blockUser(gamer, req));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("blocking severs the friendship on both sides and persists both")
        void testBlockUser_whenFriends_UnlinksBothSidesAndSavesBoth() {
            gamer.getFriends().add(other);
            other.getFriends().add(gamer);

            profileService.blockUser(gamer, request(other));

            assertTrue(gamer.getBlockedFriends().contains(other));
            assertFalse(gamer.getFriends().contains(other));
            // The old code mutated the other gamer but never saved it, so the blocked
            // user carried on seeing the blocker as a friend.
            assertFalse(other.getFriends().contains(gamer));
            verify(gamerRepository).save(other);
        }

        @Test
        void testBlockUser_whenPendingRequest_ClearsItBothWays() {
            gamer.getWaitingFriends().add(other);
            other.getWaitingFriends().add(gamer);

            profileService.blockUser(gamer, request(other));

            assertFalse(gamer.getWaitingFriends().contains(other));
            assertFalse(other.getWaitingFriends().contains(gamer));
            verify(gamerRepository).save(other);
        }

        @Test
        void testUnblockUser_whenNotBlocked_ReturnErrorCode119() {
            FriendRequest req = request(other);

            BusinessException ex = assertThrows(BusinessException.class, () -> profileService.unblockUser(gamer, req));
            assertEquals(119, ex.getTransactionCode().getId());
        }

        @Test
        void testUnblockUser_whenBlocked_Unblocks() {
            gamer.getBlockedFriends().add(other);

            profileService.unblockUser(gamer, request(other));

            assertFalse(gamer.getBlockedFriends().contains(other));
        }

        @Test
        void testSendFriendRequest_whenTargetBlockedYou_ReturnErrorCode113() {
            matched(gamer, other);
            other.getBlockedFriends().add(gamer);
            FriendRequest req = request(other);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.sendFriendRequest(gamer, req));
            // One code whichever side blocked: which it was is not the caller's business.
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        void testSendFriendRequest_whenYouBlockedTarget_ReturnErrorCode113() {
            matched(gamer, other);
            gamer.getBlockedFriends().add(other);
            FriendRequest req = request(other);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.sendFriendRequest(gamer, req));
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        void testSendFriendRequest_whenAlreadyFriends_ReturnErrorCode122() {
            matched(gamer, other);
            other.getFriends().add(gamer);
            FriendRequest req = request(other);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.sendFriendRequest(gamer, req));
            assertEquals(122, ex.getTransactionCode().getId());
        }

        @Test
        void testSendFriendRequest_whenAlreadySent_ReturnErrorCode120() {
            matched(gamer, other);
            other.getWaitingFriends().add(gamer);
            FriendRequest req = request(other);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.sendFriendRequest(gamer, req));
            assertEquals(120, ex.getTransactionCode().getId());
        }

        @Test
        void testSendFriendRequest_whenToSelf_ReturnErrorCode148() {
            FriendRequest req = request(gamer);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> profileService.sendFriendRequest(gamer, req));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        void testSendFriendRequest_whenValid_QueuesTheRequestAndNotifies() {
            matched(gamer, other);

            DefaultMessageResponse response = profileService.sendFriendRequest(gamer, request(other));

            assertEquals("100", response.getStatus().getCode());
            assertTrue(other.getWaitingFriends().contains(gamer));
            verify(events).publishEvent(any(NotificationRequestedEvent.class));
        }
    }
}
