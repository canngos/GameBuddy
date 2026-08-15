package com.gamebuddy.lobby.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.lobby.application.mapper.LobbyMapper;
import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMessage;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
import com.gamebuddy.lobby.interfaces.response.LobbyMessageResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.messaging.MessageCipher;
import com.gamebuddy.shared.messaging.UserMessaging;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class DefaultLobbyChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @InjectMocks
    private DefaultLobbyChatService chatService;

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private LobbyMemberRepository memberRepository;

    @Mock
    private LobbyMessageRepository messageRepository;

    @Mock
    private GamerRepository gamerRepository;

    private final AvatarUrls avatarUrls = mock(AvatarUrls.class);

    @Spy
    private LobbyMapper mapper = new LobbyMapper(avatarUrls);

    /** A real cipher: a mock returning null bytes would let a broken round trip pass. */
    @Spy
    private MessageCipher cipher = new MessageCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Spy
    private TextModerationService textModeration = new TextModerationService();

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private UserMessaging messaging;

    @Spy
    private RateLimiter lobbyMessageRateLimiter = new RateLimiter(1000, Duration.ofMinutes(1));

    private Gamer owner;
    private Gamer member;
    private Gamer stranger;
    private Lobby lobby;
    private LobbyMember ownerRow;
    private LobbyMember memberRow;

    @BeforeEach
    void setUp() {
        owner = newGamer("owner@example.com", "owner");
        member = newGamer("member@example.com", "member");
        stranger = newGamer("stranger@example.com", "stranger");

        lobby = new Lobby();
        lobby.setId(UUID.randomUUID());
        lobby.setOwnerId(owner.getUserId());
        lobby.setGameId("game-1");
        lobby.setTitle("ranked grind");
        lobby.setTone(LobbyTone.COMPETITIVE);
        lobby.setMaxPlayers(3);
        lobby.setStartsAt(NOW.plus(Duration.ofHours(2)));
        lobby.setStatus(LobbyStatus.OPEN);

        ownerRow = new LobbyMember(lobby.getId(), owner.getUserId(), LobbyMemberStatus.OWNER, NOW);
        memberRow = new LobbyMember(lobby.getId(), member.getUserId(), LobbyMemberStatus.ACCEPTED, NOW);

        when(lobbyRepository.findById(lobby.getId())).thenReturn(Optional.of(lobby));
        when(gamerRepository.findById(owner.getUserId())).thenReturn(Optional.of(owner));
        when(gamerRepository.findById(member.getUserId())).thenReturn(Optional.of(member));
        when(gamerRepository.findById(stranger.getUserId())).thenReturn(Optional.of(stranger));
        when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), owner.getUserId()))
                .thenReturn(Optional.of(ownerRow));
        when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), member.getUserId()))
                .thenReturn(Optional.of(memberRow));
        when(memberRepository.findByLobbyIdAndUserId(lobby.getId(), stranger.getUserId()))
                .thenReturn(Optional.empty());
        when(memberRepository.findAllByLobbyId(lobby.getId())).thenReturn(List.of(ownerRow, memberRow));
        when(gamerRepository.findAllById(any())).thenAnswer(inv -> {
            List<Gamer> found = new ArrayList<>();
            for (String id : (Iterable<String>) inv.getArgument(0)) {
                for (Gamer g : List.of(owner, member, stranger)) {
                    if (g.getUserId().equals(id)) {
                        found.add(g);
                    }
                }
            }
            return found;
        });
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        return g;
    }

    private static TransactionCode codeOf(Runnable call) {
        BusinessException e = assertThrows(BusinessException.class, call::run);
        return e.getTransactionCode();
    }

    @Test
    @DisplayName("a send is stored encrypted, fanned out to the team's emails minus the author, and pushed")
    void testSend_whenTeamMember_StoresFansOutAndNotifies() {
        LobbyMessageResponse response = chatService.send(member, lobby.getId(), "who has a fifth?");

        // Stored ciphertext, never plaintext.
        ArgumentCaptor<LobbyMessage> saved = ArgumentCaptor.forClass(LobbyMessage.class);
        verify(messageRepository).save(saved.capture());
        assertNotEquals(
                "who has a fifth?", new String(saved.getValue().getBody(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(
                "who has a fifth?",
                cipher.decrypt(saved.getValue().getBody(), saved.getValue().getNonce(), saved.getValue()
                        .getKeyVersion()));

        // Fan-out to the owner's email — the STOMP principal — and not back to the author.
        verify(messaging).sendToUser(eq("owner@example.com"), eq("/queue/lobby"), any());
        verify(messaging, never()).sendToUser(eq("member@example.com"), any(), any());

        // One queued push, kind LOBBY_MESSAGE, inside the same transaction.
        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(NotificationKind.LOBBY_MESSAGE, event.getValue().kind());
        assertEquals(owner.getUserId(), event.getValue().recipientId());

        // The author gets the screened text back.
        assertEquals("who has a fifth?", response.getBody().getData().getMessage().getMessage());
    }

    @Test
    @DisplayName("somebody the owner never accepted is refused")
    void testSend_whenNotTeamMember_RefusesWithNotMember() {
        assertEquals(TransactionCode.LOBBY_NOT_MEMBER, codeOf(() -> chatService.send(stranger, lobby.getId(), "hi")));
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("a LOCKED lobby still chats — locking without a timer is the point")
    void testSend_whenLocked_StillWorks() {
        lobby.setStatus(LobbyStatus.LOCKED);

        assertDoesNotThrow(() -> chatService.send(member, lobby.getId(), "see you at seven"));
    }

    @Test
    @DisplayName("an ended or cancelled lobby is read-only")
    void testSend_whenFinished_RefusesWithNotOpen() {
        lobby.setStatus(LobbyStatus.ENDED);
        assertEquals(TransactionCode.LOBBY_NOT_OPEN, codeOf(() -> chatService.send(member, lobby.getId(), "gg")));

        lobby.setStatus(LobbyStatus.CANCELLED);
        assertEquals(TransactionCode.LOBBY_NOT_OPEN, codeOf(() -> chatService.send(member, lobby.getId(), "gg")));
    }

    @Test
    @DisplayName("a slur is refused before it is ever encrypted")
    void testSend_whenSlur_RefusesWithContentBlocked() {
        assertEquals(TransactionCode.CONTENT_BLOCKED, codeOf(() -> chatService.send(member, lobby.getId(), "kys")));
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("reading the history moves the reader's watermark and decrypts in order")
    void testMessages_whenTeamMember_ReadsAndMarksRead() {
        MessageCipher.Encrypted encrypted = cipher.encrypt("first");
        LobbyMessage stored = new LobbyMessage();
        stored.setId(UUID.randomUUID());
        stored.setLobbyId(lobby.getId());
        stored.setSenderId(owner.getUserId());
        stored.setBody(encrypted.ciphertext());
        stored.setNonce(encrypted.nonce());
        stored.setKeyVersion(MessageCipher.CURRENT_KEY_VERSION);
        stored.setCreatedAt(NOW.minus(Duration.ofMinutes(5)));
        when(messageRepository.findAllByLobbyIdOrderByCreatedAtAsc(lobby.getId())).thenReturn(List.of(stored));

        var response = chatService.messages(member, lobby.getId());

        assertEquals(NOW, memberRow.getLastReadAt(), "reading is what moves the watermark");
        assertEquals("first", response.getBody().getData().getMessages().get(0).getMessage());
        assertEquals("owner", response.getBody().getData().getMessages().get(0).getSenderUsername());
    }

    @Test
    @DisplayName("an ended lobby's history stays readable — what was that guy's Discord again?")
    void testMessages_whenEnded_StillReadable() {
        lobby.setStatus(LobbyStatus.ENDED);
        when(messageRepository.findAllByLobbyIdOrderByCreatedAtAsc(lobby.getId())).thenReturn(List.of());

        assertDoesNotThrow(() -> chatService.messages(member, lobby.getId()));
    }

    @Test
    @DisplayName("the message limiter answers RATE_LIMITED when exhausted")
    void testSend_whenRateLimited_Refuses() {
        doReturn(false).when(lobbyMessageRateLimiter).tryAcquire(member.getUserId());

        assertEquals(TransactionCode.RATE_LIMITED, codeOf(() -> chatService.send(member, lobby.getId(), "spam")));
    }
}
