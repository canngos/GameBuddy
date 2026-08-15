package com.gamebuddy.lobby.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.lobby.application.mapper.LobbyMapper;
import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
import com.gamebuddy.lobby.interfaces.dto.LobbyEvent;
import com.gamebuddy.lobby.interfaces.request.CreateLobbyRequest;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.messaging.UserMessaging;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.repository.GamesRepository;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultLobbyServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @InjectMocks
    private DefaultLobbyService lobbyService;

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private LobbyMemberRepository memberRepository;

    @Mock
    private LobbyMessageRepository messageRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private GamesRepository gamesRepository;

    /** Created inline rather than with {@code @Mock}: the mapper needs it at field-init time. */
    private final AvatarUrls avatarUrls = mock(AvatarUrls.class);

    /** The real mapper over a mocked avatar resolver; DTO wiring is worth exercising. */
    @Spy
    private LobbyMapper mapper = new LobbyMapper(avatarUrls);

    /** The real filter: what matters is whether a slur actually reaches the database. */
    @Spy
    private TextModerationService textModeration = new TextModerationService();

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private UserMessaging messaging;

    @Spy
    private RateLimiter lobbyCreateRateLimiter = new RateLimiter(1000, Duration.ofDays(1));

    @Spy
    private RateLimiter lobbyJoinRateLimiter = new RateLimiter(1000, Duration.ofHours(1));

    private Gamer owner;
    private Gamer requester;
    private Games game;
    private Lobby lobby;

    @BeforeEach
    void setUp() {
        owner = newGamer("owner@example.com", "owner");
        gold(owner);
        requester = newGamer("requester@example.com", "requester");

        game = new Games();
        game.setGameId("game-1");
        game.setGameName("Valorant");

        lobby = new Lobby();
        lobby.setId(UUID.randomUUID());
        lobby.setOwnerId(owner.getUserId());
        lobby.setGameId(game.getGameId());
        lobby.setTitle("ranked grind");
        lobby.setTone(LobbyTone.COMPETITIVE);
        lobby.setMaxPlayers(3);
        lobby.setStartsAt(NOW.plus(Duration.ofHours(2)));
        lobby.setStatus(LobbyStatus.OPEN);

        when(gamerRepository.findById(owner.getUserId())).thenReturn(Optional.of(owner));
        when(gamerRepository.findById(requester.getUserId())).thenReturn(Optional.of(requester));
        when(gamerRepository.findAllById(any())).thenAnswer(inv -> {
            List<Gamer> found = new ArrayList<>();
            for (String id : (Iterable<String>) inv.getArgument(0)) {
                if (id.equals(owner.getUserId())) {
                    found.add(owner);
                } else if (id.equals(requester.getUserId())) {
                    found.add(requester);
                }
            }
            return found;
        });
        when(gamesRepository.findById(game.getGameId())).thenReturn(Optional.of(game));
        when(lobbyRepository.findById(lobby.getId())).thenReturn(Optional.of(lobby));
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        g.setAge(25);
        g.setSubscriptionTier(SubscriptionTier.BASIC);
        return g;
    }

    private static void gold(Gamer gamer) {
        gamer.setSubscriptionTier(SubscriptionTier.GOLD);
        gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));
    }

    private CreateLobbyRequest createRequest() {
        CreateLobbyRequest request = new CreateLobbyRequest();
        request.setGameId(game.getGameId());
        request.setTitle("ranked grind");
        request.setTone(LobbyTone.COMPETITIVE);
        request.setMaxPlayers(3);
        request.setStartsAt(NOW.plus(Duration.ofHours(2)));
        return request;
    }

    private LobbyMember member(String userId, LobbyMemberStatus status) {
        return new LobbyMember(lobby.getId(), userId, status, NOW);
    }

    private static TransactionCode codeOf(Runnable call) {
        BusinessException e = assertThrows(BusinessException.class, call::run);
        return e.getTransactionCode();
    }

    @Nested
    class Browse {

        private final Pageable page = PageRequest.of(0, 20);

        @Test
        @DisplayName("without the now filter, the start bound is far enough away to mean 'no bound'")
        void testBrowse_whenUnfiltered_AppliesNoEffectiveStartBound() {
            when(lobbyRepository.findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), any(), eq(page)))
                    .thenReturn(new PageImpl<>(List.of(lobby)));

            lobbyService.browse(owner, null, null, false, page);

            ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
            verify(lobbyRepository)
                    .findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), bound.capture(), eq(page));
            // Beyond any startsAt creation will accept, so every open lobby is included.
            assertTrue(bound.getValue().isAfter(NOW.plus(DefaultLobbyService.STARTS_AT_HORIZON)));
        }

        @Test
        @DisplayName("the now filter bounds the query at a quarter of an hour from now")
        void testBrowse_whenStartingSoon_BoundsAtFifteenMinutes() {
            when(lobbyRepository.findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), any(), eq(page)))
                    .thenReturn(new PageImpl<>(List.of(lobby)));

            lobbyService.browse(owner, null, null, true, page);

            ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
            verify(lobbyRepository)
                    .findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), bound.capture(), eq(page));
            assertEquals(NOW.plus(DefaultLobbyService.STARTING_SOON), bound.getValue());
        }

        @Test
        @DisplayName("the now filter narrows alongside tone rather than replacing it")
        void testBrowse_whenStartingSoonAndTone_CombinesBoth() {
            when(lobbyRepository.findAllByStatusAndStartsAtBeforeAndToneOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), any(), eq(LobbyTone.COMPETITIVE), eq(page)))
                    .thenReturn(new PageImpl<>(List.of(lobby)));

            lobbyService.browse(owner, null, LobbyTone.COMPETITIVE, true, page);

            ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
            verify(lobbyRepository)
                    .findAllByStatusAndStartsAtBeforeAndToneOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), bound.capture(), eq(LobbyTone.COMPETITIVE), eq(page));
            assertEquals(NOW.plus(DefaultLobbyService.STARTING_SOON), bound.getValue());
        }

        @Test
        @DisplayName("a lobby whose stated time just passed still counts as starting now")
        void testBrowse_whenStartAlreadyPassed_IsIncludedByTheBound() {
            // The bound is an upper one only: somebody is sitting in that lobby waiting,
            // which is the most "about to start" a lobby gets.
            lobby.setStartsAt(NOW.minus(Duration.ofMinutes(20)));
            when(lobbyRepository.findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), any(), eq(page)))
                    .thenReturn(new PageImpl<>(List.of(lobby)));

            var response = lobbyService.browse(owner, null, null, true, page);

            assertEquals(1, response.getBody().getData().getLobbies().size());
        }

        @Test
        @DisplayName("a blocked pair's lobby is left out of the feed entirely")
        void testBrowse_whenOwnerBlocked_OmitsTheLobby() {
            owner.getBlockedFriends().add(requester);
            when(lobbyRepository.findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
                            eq(LobbyStatus.OPEN), any(), eq(page)))
                    .thenReturn(new PageImpl<>(List.of(lobby)));

            var response = lobbyService.browse(requester, null, null, false, page);

            assertTrue(response.getBody().getData().getLobbies().isEmpty());
        }
    }

    @Nested
    class Create {

        @Test
        @DisplayName("a free-tier gamer is answered SUBSCRIPTION_REQUIRED, which the app routes to the paywall")
        void testCreate_whenBasicTier_RefusesWithSubscriptionRequired() {
            Gamer basic = newGamer("basic@example.com", "basic");
            when(gamerRepository.findById(basic.getUserId())).thenReturn(Optional.of(basic));

            assertEquals(TransactionCode.SUBSCRIPTION_REQUIRED, codeOf(() -> lobbyService.create(basic, createRequest())));
            verify(lobbyRepository, never()).save(any());
        }

        @Test
        @DisplayName("a lapsed Gold is BASIC in effect — the stored tier column still says GOLD")
        void testCreate_whenGoldExpired_RefusesWithSubscriptionRequired() {
            owner.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(1)));

            assertEquals(TransactionCode.SUBSCRIPTION_REQUIRED, codeOf(() -> lobbyService.create(owner, createRequest())));
        }

        @Test
        @DisplayName("a Gold member gets a lobby and an OWNER member row in one act")
        void testCreate_whenGold_SavesLobbyAndOwnerRow() {
            lobbyService.create(owner, createRequest());

            ArgumentCaptor<Lobby> saved = ArgumentCaptor.forClass(Lobby.class);
            verify(lobbyRepository).save(saved.capture());
            assertEquals(LobbyStatus.OPEN, saved.getValue().getStatus());
            assertEquals(owner.getUserId(), saved.getValue().getOwnerId());

            ArgumentCaptor<LobbyMember> row = ArgumentCaptor.forClass(LobbyMember.class);
            verify(memberRepository).save(row.capture());
            assertEquals(LobbyMemberStatus.OWNER, row.getValue().getStatus());
        }

        @Test
        @DisplayName("one live lobby per owner — a second is refused while the first is OPEN or LOCKED")
        void testCreate_whenActiveLobbyExists_RefusesWithLimitReached() {
            when(lobbyRepository.existsByOwnerIdAndStatusIn(eq(owner.getUserId()), any()))
                    .thenReturn(true);

            assertEquals(TransactionCode.LOBBY_LIMIT_REACHED, codeOf(() -> lobbyService.create(owner, createRequest())));
        }

        @Test
        @DisplayName("a slur in the title never reaches the database")
        void testCreate_whenTitleContainsSlur_RefusesWithContentBlocked() {
            CreateLobbyRequest request = createRequest();
            request.setTitle("kys noobs");

            assertEquals(TransactionCode.CONTENT_BLOCKED, codeOf(() -> lobbyService.create(owner, request)));
            verify(lobbyRepository, never()).save(any());
        }

        @Test
        @DisplayName("a planned start in the past is refused; fifteen minutes of clock skew is not")
        void testCreate_startsAtBounds() {
            CreateLobbyRequest longAgo = createRequest();
            longAgo.setStartsAt(NOW.minus(Duration.ofHours(1)));
            assertEquals(TransactionCode.INVALID_REQUEST, codeOf(() -> lobbyService.create(owner, longAgo)));

            CreateLobbyRequest farAway = createRequest();
            farAway.setStartsAt(NOW.plus(Duration.ofDays(30)));
            assertEquals(TransactionCode.INVALID_REQUEST, codeOf(() -> lobbyService.create(owner, farAway)));

            CreateLobbyRequest justNow = createRequest();
            justNow.setStartsAt(NOW.minus(Duration.ofMinutes(5)));
            assertDoesNotThrow(() -> lobbyService.create(owner, justNow));
        }

        @Test
        @DisplayName("the create limiter answers RATE_LIMITED when exhausted")
        void testCreate_whenRateLimited_Refuses() {
            // Both spies are stubbed: two same-type limiters leave @InjectMocks free to
            // wire either one into either field, and only the create path runs here.
            doReturn(false).when(lobbyCreateRateLimiter).tryAcquire(anyString());
            doReturn(false).when(lobbyJoinRateLimiter).tryAcquire(anyString());

            assertEquals(TransactionCode.RATE_LIMITED, codeOf(() -> lobbyService.create(owner, createRequest())));
        }
    }

    @Nested
    class Join {

        @Test
        @DisplayName("a join is a PENDING row, a push to the owner, and a socket frame to the owner's email")
        void testJoin_whenOpen_CreatesPendingAndNotifiesOwner() {
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.empty());
            when(memberRepository.countByLobbyIdAndStatusIn(eq(lobby.getId()), any()))
                    .thenReturn(1L);

            lobbyService.join(requester, lobby.getId());

            ArgumentCaptor<LobbyMember> row = ArgumentCaptor.forClass(LobbyMember.class);
            verify(memberRepository).save(row.capture());
            assertEquals(LobbyMemberStatus.PENDING, row.getValue().getStatus());

            ArgumentCaptor<NotificationRequestedEvent> event =
                    ArgumentCaptor.forClass(NotificationRequestedEvent.class);
            verify(events).publishEvent(event.capture());
            assertEquals(NotificationKind.LOBBY_JOIN_REQUEST, event.getValue().kind());

            // The STOMP principal is the email; a userId here delivers to nobody, silently.
            verify(messaging).sendToUser(eq("owner@example.com"), eq("/queue/lobby"), any(LobbyEvent.class));
        }

        @Test
        @DisplayName("a full lobby refuses further requests")
        void testJoin_whenFull_RefusesWithLobbyFull() {
            when(memberRepository.countByLobbyIdAndStatusIn(eq(lobby.getId()), any()))
                    .thenReturn(3L);

            assertEquals(TransactionCode.LOBBY_FULL, codeOf(() -> lobbyService.join(requester, lobby.getId())));
        }

        @Test
        @DisplayName("a locked lobby refuses — that is what locking is for")
        void testJoin_whenLocked_RefusesWithNotOpen() {
            lobby.setStatus(LobbyStatus.LOCKED);

            assertEquals(TransactionCode.LOBBY_NOT_OPEN, codeOf(() -> lobbyService.join(requester, lobby.getId())));
        }

        @Test
        @DisplayName("a rejection is final for this lobby — asking again is refused, not re-queued")
        void testJoin_whenPreviouslyRejected_RefusesWithRejected() {
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(member(requester.getUserId(), LobbyMemberStatus.REJECTED)));

            assertEquals(TransactionCode.LOBBY_REJECTED, codeOf(() -> lobbyService.join(requester, lobby.getId())));
        }

        @Test
        @DisplayName("asking twice is refused by the row that already exists")
        void testJoin_whenAlreadyPending_RefusesWithAlreadyMember() {
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(member(requester.getUserId(), LobbyMemberStatus.PENDING)));

            assertEquals(
                    TransactionCode.LOBBY_ALREADY_MEMBER, codeOf(() -> lobbyService.join(requester, lobby.getId())));
        }

        @Test
        @DisplayName("somebody who left may ask again — the same row asks")
        void testJoin_whenPreviouslyLeft_FlipsRowBackToPending() {
            LobbyMember row = member(requester.getUserId(), LobbyMemberStatus.LEFT);
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(row));

            lobbyService.join(requester, lobby.getId());

            assertEquals(LobbyMemberStatus.PENDING, row.getStatus());
            assertNull(row.getDecidedAt());
        }

        @Test
        @DisplayName("blocked pairs do not meet in lobbies, in either direction")
        void testJoin_whenBlockRelationship_RefusesWithUserBlocked() {
            owner.getBlockedFriends().add(requester);

            assertEquals(TransactionCode.USER_BLOCKED, codeOf(() -> lobbyService.join(requester, lobby.getId())));
        }
    }

    @Nested
    class AcceptAndReject {

        @Test
        @DisplayName("accepting advances the row, tells the requester, and touches the lobby for the version lock")
        void testAccept_whenPending_AcceptsAndNotifies() {
            LobbyMember row = member(requester.getUserId(), LobbyMemberStatus.PENDING);
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(row));
            when(memberRepository.countByLobbyIdAndStatusIn(eq(lobby.getId()), any()))
                    .thenReturn(1L);
            when(memberRepository.findAllByLobbyId(lobby.getId()))
                    .thenReturn(List.of(
                            member(owner.getUserId(), LobbyMemberStatus.OWNER),
                            row));

            lobbyService.accept(owner, lobby.getId(), requester.getUserId());

            assertEquals(LobbyMemberStatus.ACCEPTED, row.getStatus());
            assertEquals(NOW, row.getDecidedAt());
            // The lobby row is written too, so accept-vs-cancel races meet the @Version lock.
            verify(lobbyRepository).save(lobby);

            ArgumentCaptor<NotificationRequestedEvent> event =
                    ArgumentCaptor.forClass(NotificationRequestedEvent.class);
            verify(events).publishEvent(event.capture());
            assertEquals(NotificationKind.LOBBY_REQUEST_ACCEPTED, event.getValue().kind());
        }

        @Test
        @DisplayName("only the owner accepts")
        void testAccept_whenNotOwner_RefusesWithForbidden() {
            assertEquals(
                    TransactionCode.FORBIDDEN,
                    codeOf(() -> lobbyService.accept(requester, lobby.getId(), requester.getUserId())));
        }

        @Test
        @DisplayName("the last seat cannot be over-filled")
        void testAccept_whenFull_RefusesWithLobbyFull() {
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(member(requester.getUserId(), LobbyMemberStatus.PENDING)));
            when(memberRepository.countByLobbyIdAndStatusIn(eq(lobby.getId()), any()))
                    .thenReturn(3L);

            assertEquals(
                    TransactionCode.LOBBY_FULL,
                    codeOf(() -> lobbyService.accept(owner, lobby.getId(), requester.getUserId())));
        }

        @Test
        @DisplayName("rejecting is quiet: the row advances and no push is sent")
        void testReject_whenPending_RejectsWithoutNotification() {
            LobbyMember row = member(requester.getUserId(), LobbyMemberStatus.PENDING);
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(row));

            lobbyService.reject(owner, lobby.getId(), requester.getUserId());

            assertEquals(LobbyMemberStatus.REJECTED, row.getStatus());
            verify(events, never()).publishEvent(any());
            verify(messaging, never()).sendToUser(any(), any(), any());
        }

        @Test
        @DisplayName("answering a request that is not there is NOT_FOUND, not a silent success")
        void testAccept_whenNoRequest_RefusesWithRequestNotFound() {
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.empty());

            assertEquals(
                    TransactionCode.LOBBY_REQUEST_NOT_FOUND,
                    codeOf(() -> lobbyService.accept(owner, lobby.getId(), requester.getUserId())));
        }
    }

    @Nested
    class Lifecycle {

        @Test
        @DisplayName("locking quietly rejects the queue and starts no timer of any kind")
        void testLock_whenOpen_LocksAndSilentlyRejectsPending() {
            LobbyMember pending = member(requester.getUserId(), LobbyMemberStatus.PENDING);
            when(memberRepository.findAllByLobbyIdAndStatus(lobby.getId(), LobbyMemberStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(memberRepository.findAllByLobbyId(lobby.getId()))
                    .thenReturn(List.of(member(owner.getUserId(), LobbyMemberStatus.OWNER)));

            lobbyService.lock(owner, lobby.getId());

            assertEquals(LobbyStatus.LOCKED, lobby.getStatus());
            assertEquals(NOW, lobby.getLockedAt());
            assertNull(lobby.getEndedAt(), "locking must not schedule or record any end");
            assertEquals(LobbyMemberStatus.REJECTED, pending.getStatus());
            // Quietly: no push for being passed over.
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("a locked lobby cannot be cancelled — the owner unlocks first, deliberately")
        void testCancel_whenLocked_RefusesWithNotOpen() {
            lobby.setStatus(LobbyStatus.LOCKED);

            assertEquals(TransactionCode.LOBBY_NOT_OPEN, codeOf(() -> lobbyService.cancel(owner, lobby.getId())));
            assertEquals(LobbyStatus.LOCKED, lobby.getStatus());
        }

        @Test
        @DisplayName("unlock reopens the lobby when somebody bailed")
        void testUnlock_whenLocked_Reopens() {
            lobby.setStatus(LobbyStatus.LOCKED);
            lobby.setLockedAt(NOW.minus(Duration.ofHours(1)));
            when(memberRepository.findAllByLobbyId(lobby.getId()))
                    .thenReturn(List.of(member(owner.getUserId(), LobbyMemberStatus.OWNER)));

            lobbyService.unlock(owner, lobby.getId());

            assertEquals(LobbyStatus.OPEN, lobby.getStatus());
            assertNull(lobby.getLockedAt());
        }

        @Test
        @DisplayName("ending is only reachable from LOCKED")
        void testEnd_transitions() {
            assertEquals(TransactionCode.LOBBY_NOT_OPEN, codeOf(() -> lobbyService.end(owner, lobby.getId())));

            lobby.setStatus(LobbyStatus.LOCKED);
            when(memberRepository.findAllByLobbyId(lobby.getId()))
                    .thenReturn(List.of(member(owner.getUserId(), LobbyMemberStatus.OWNER)));
            lobbyService.end(owner, lobby.getId());

            assertEquals(LobbyStatus.ENDED, lobby.getStatus());
            assertEquals(NOW, lobby.getEndedAt());
        }

        @Test
        @DisplayName("cancelling from OPEN tells the accepted members and nobody else")
        void testCancel_whenOpen_CancelsAndNotifiesAccepted() {
            when(memberRepository.findAllByLobbyIdAndStatus(lobby.getId(), LobbyMemberStatus.ACCEPTED))
                    .thenReturn(List.of(member(requester.getUserId(), LobbyMemberStatus.ACCEPTED)));
            when(memberRepository.findAllByLobbyId(lobby.getId()))
                    .thenReturn(List.of(member(owner.getUserId(), LobbyMemberStatus.OWNER)));

            lobbyService.cancel(owner, lobby.getId());

            assertEquals(LobbyStatus.CANCELLED, lobby.getStatus());
            ArgumentCaptor<NotificationRequestedEvent> event =
                    ArgumentCaptor.forClass(NotificationRequestedEvent.class);
            verify(events).publishEvent(event.capture());
            assertEquals(NotificationKind.LOBBY_CANCELLED, event.getValue().kind());
            assertEquals(requester.getUserId(), event.getValue().recipientId());
        }

        @Test
        @DisplayName("the owner does not leave their own lobby; members do, and their row says LEFT")
        void testLeave() {
            assertEquals(TransactionCode.FORBIDDEN, codeOf(() -> lobbyService.leave(owner, lobby.getId())));

            LobbyMember row = member(requester.getUserId(), LobbyMemberStatus.ACCEPTED);
            when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), requester.getUserId()))
                    .thenReturn(Optional.of(row));
            when(memberRepository.findAllByLobbyId(lobby.getId()))
                    .thenReturn(List.of(member(owner.getUserId(), LobbyMemberStatus.OWNER)));

            lobbyService.leave(requester, lobby.getId());
            assertEquals(LobbyMemberStatus.LEFT, row.getStatus());
        }
    }
}
