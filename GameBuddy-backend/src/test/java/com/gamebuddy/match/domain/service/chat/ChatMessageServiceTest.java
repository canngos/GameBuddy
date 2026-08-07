package com.gamebuddy.match.domain.service.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import com.gamebuddy.match.infrastructure.entity.ChatParticipant;
import com.gamebuddy.match.infrastructure.entity.ChatRoom;
import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.match.infrastructure.repository.ChatParticipantRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.AvatarsRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMessageServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    /**
     * The real filter, not a mock: it is a pure function over a word list, so a stub would
     * only prove a stub was called. What matters is whether a slur actually gets stored.
     */
    @Spy
    private TextModerationService textModeration = new TextModerationService();

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatParticipantRepository participantRepository;

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
    private ChatRoomService chatRoomService;

    /** Sending a message now queues a push for the recipient. */
    @Mock
    private ApplicationEventPublisher events;

    /**
     * A real cipher, not a mock. Encryption is on the path every message takes, and a mock
     * returning null bytes would let a broken round trip pass. The key here is a throwaway.
     */
    @Spy
    private MessageCipher cipher = new MessageCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private Gamer sender;
    private Gamer receiver;
    private Gamer stranger;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        sender = newGamer("sender@example.com", "sender");
        receiver = newGamer("receiver@example.com", "receiver");
        stranger = newGamer("stranger@example.com", "stranger");

        // sender and receiver have matched; stranger has not.
        sender.getApprovedMatches().add(receiver);
        receiver.getApprovedMatches().add(sender);

        room = new ChatRoom();
        room.setId(UUID.randomUUID());
        room.setPairKey(ChatRoom.pairKeyFor(sender.getUserId(), receiver.getUserId()));
        room.setCreatedAt(NOW);

        when(gamerRepository.findById(sender.getUserId())).thenReturn(Optional.of(sender));
        when(gamerRepository.findById(receiver.getUserId())).thenReturn(Optional.of(receiver));
        when(gamerRepository.findById(stranger.getUserId())).thenReturn(Optional.of(stranger));
        when(chatRoomService.findOrCreate(anyString(), anyString())).thenReturn(room);
        when(chatRoomService.find(anyString(), anyString())).thenReturn(Optional.of(room));
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        g.setAge(25); // adults by default; the age-band cases set their own
        return g;
    }

    /** A message as it would be stored: encrypted, with its nonce. */
    private ChatMessage storedMessage(String from, String text) {
        MessageCipher.Encrypted encrypted = cipher.encrypt(text);
        ChatMessage m = new ChatMessage();
        m.setId(UUID.randomUUID());
        m.setRoomId(room.getId());
        m.setSenderId(from);
        m.setBody(encrypted.ciphertext());
        m.setNonce(encrypted.nonce());
        m.setKeyVersion(MessageCipher.CURRENT_KEY_VERSION);
        m.setCreatedAt(NOW);
        return m;
    }

    @Nested
    class Save {

        @Test
        @DisplayName("the server assigns sender and timestamp; the client supplies neither")
        void testSave_whenCalled_ServerOwnsTheMetadata() {
            SentMessage sent = chatMessageService.save(sender.getUserId(), receiver.getUserId(), "hello");

            assertEquals(sender.getUserId(), sent.senderId());
            assertEquals("sender", sent.senderName());
            assertEquals("hello", sent.body());
            assertEquals(NOW, sent.sentAt(), "the timestamp used to arrive from the client");
            assertEquals("receiver@example.com", sent.receiverPrincipalName());
            verify(messageRepository).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("the body reaches the database encrypted, never as plaintext")
        void testSave_whenCalled_StoresCiphertext() {
            chatMessageService.save(sender.getUserId(), receiver.getUserId(), "something private");

            var captor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
            verify(messageRepository).save(captor.capture());
            ChatMessage stored = captor.getValue();

            assertFalse(
                    new String(stored.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("something private"),
                    "a leaked dump must not contain readable messages");
            assertNotNull(stored.getNonce());
            assertEquals(
                    "something private",
                    cipher.decrypt(stored.getBody(), stored.getNonce(), stored.getKeyVersion()),
                    "and it must round trip");
        }

        @Test
        @DisplayName("every message gets its own nonce; reusing one under a key breaks GCM")
        void testSave_whenCalledTwice_UsesDistinctNonces() {
            chatMessageService.save(sender.getUserId(), receiver.getUserId(), "same text");
            chatMessageService.save(sender.getUserId(), receiver.getUserId(), "same text");

            var captor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
            verify(messageRepository, times(2)).save(captor.capture());
            List<ChatMessage> saved = captor.getAllValues();

            assertFalse(Arrays.equals(saved.get(0).getNonce(), saved.get(1).getNonce()));
            assertFalse(Arrays.equals(saved.get(0).getBody(), saved.get(1).getBody()));
        }

        @Test
        @DisplayName("you cannot message someone you have not matched with")
        void testSave_whenNotMatched_ReturnErrorCode153() {
            String from = sender.getUserId();
            String to = stranger.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> chatMessageService.save(from, to, "hi"));
            assertEquals(153, ex.getTransactionCode().getId());
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("a one-sided match is not enough")
        void testSave_whenMatchIsOneSided_ReturnErrorCode153() {
            sender.getApprovedMatches().add(stranger);
            String from = sender.getUserId();
            String to = stranger.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> chatMessageService.save(from, to, "hi"));
            assertEquals(153, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("blocking ends an existing conversation immediately")
        void testSave_whenBlockedAfterMatching_ReturnErrorCode113() {
            // The pair matched first, then one blocked the other. Nothing rewrote the
            // match tables, so without this check the conversation carried on.
            sender.getBlockedFriends().add(receiver);
            String from = sender.getUserId();
            String to = receiver.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> chatMessageService.save(from, to, "hi"));
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("being blocked ends it too, not just doing the blocking")
        void testSave_whenBlockedByReceiver_ReturnErrorCode113() {
            receiver.getBlockedFriends().add(sender);
            String from = sender.getUserId();
            String to = receiver.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> chatMessageService.save(from, to, "hi"));
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a minor and an adult cannot exchange messages even if already matched")
        void testSave_whenAgeBandsDiffer_ReturnErrorCode154() {
            // Covers the case where one gamer edits their age after matching.
            sender.setAge(16);
            receiver.setAge(30);
            String from = sender.getUserId();
            String to = receiver.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> chatMessageService.save(from, to, "hi"));
            assertEquals(154, ex.getTransactionCode().getId());
        }

        @Test
        void testSave_whenBothMinors_Succeeds() {
            sender.setAge(15);
            receiver.setAge(17);

            assertDoesNotThrow(() -> chatMessageService.save(sender.getUserId(), receiver.getUserId(), "hi"));
        }

        @Test
        void testSave_whenMessagingYourself_ReturnErrorCode148() {
            String id = sender.getUserId();

            BusinessException ex = assertThrows(BusinessException.class, () -> chatMessageService.save(id, id, "hi"));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        void testSave_whenReceiverUnknown_ReturnErrorCode103() {
            when(gamerRepository.findById("ghost")).thenReturn(Optional.empty());
            String from = sender.getUserId();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> chatMessageService.save(from, "ghost", "hi"));
            assertEquals(103, ex.getTransactionCode().getId());
        }
    }

    @Nested
    class Conversation {

        @Test
        @DisplayName("messages come back decrypted and in order")
        void testFindChatMessages_whenCalled_DecryptsInOrder() {
            ChatMessage first = storedMessage(sender.getUserId(), "first");
            ChatMessage second = storedMessage(receiver.getUserId(), "second");
            when(messageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId()))
                    .thenReturn(List.of(first, second));

            var conversations = chatMessageService
                    .findChatMessages(receiver, sender.getUserId())
                    .getBody()
                    .getData()
                    .getConversations();

            assertEquals(2, conversations.size());
            assertEquals("first", conversations.get(0).getMessage());
            assertEquals("second", conversations.get(1).getMessage());
        }

        @Test
        @DisplayName("opening a conversation moves the watermark, rather than rewriting every message")
        void testFindChatMessages_whenCalled_MovesTheReadWatermark() {
            ChatParticipant participant = new ChatParticipant(room.getId(), receiver.getUserId());
            when(participantRepository.findByRoomIdAndUserId(room.getId(), receiver.getUserId()))
                    .thenReturn(Optional.of(participant));
            ChatMessage stored = storedMessage(sender.getUserId(), "hi");
            when(messageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId()))
                    .thenReturn(List.of(stored));

            chatMessageService.findChatMessages(receiver, sender.getUserId());

            assertEquals(NOW, participant.getLastReadAt());
            verify(participantRepository).save(participant);
            // One small row, not one update per message plus one index write per message.
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("a conversation that has never been opened is empty rather than an error")
        void testFindChatMessages_whenNoRoom_ReturnsEmpty() {
            when(chatRoomService.find(anyString(), anyString())).thenReturn(Optional.empty());

            var conversations = chatMessageService
                    .findChatMessages(receiver, stranger.getUserId())
                    .getBody()
                    .getData()
                    .getConversations();

            assertTrue(conversations.isEmpty());
        }
    }

    @Nested
    class Inbox {

        @Test
        @DisplayName("an empty room is skipped rather than taking the whole inbox down")
        void testFindInbox_whenRoomHasNoMessages_SkipsIt() {
            when(participantRepository.findAllByUserId(receiver.getUserId()))
                    .thenReturn(List.of(new ChatParticipant(room.getId(), receiver.getUserId())));
            when(messageRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId()))
                    .thenReturn(Optional.empty());

            var inbox =
                    chatMessageService.findInbox(receiver).getBody().getData().getInboxList();

            assertTrue(inbox.isEmpty(), "an empty room used to NPE and lose every other conversation");
        }

        @Test
        @DisplayName("the preview is decrypted and the unread count comes from the watermark")
        void testFindInbox_whenRoomHasMessages_ReturnsDecryptedPreview() {
            ChatParticipant participant = new ChatParticipant(room.getId(), receiver.getUserId());
            when(participantRepository.findAllByUserId(receiver.getUserId())).thenReturn(List.of(participant));
            when(participantRepository.findAllByRoomId(room.getId()))
                    .thenReturn(List.of(participant, new ChatParticipant(room.getId(), sender.getUserId())));
            ChatMessage latest = storedMessage(sender.getUserId(), "latest text");
            when(messageRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId()))
                    .thenReturn(Optional.of(latest));
            // EPOCH, not null: a conversation never opened has no watermark, and passing
            // null made Postgres reject the query with "could not determine data type of
            // parameter". The mock pins the translation so it cannot drift back.
            when(messageRepository.countUnread(room.getId(), receiver.getUserId(), Instant.EPOCH))
                    .thenReturn(3L);

            var inbox =
                    chatMessageService.findInbox(receiver).getBody().getData().getInboxList();

            assertEquals(1, inbox.size());
            assertEquals("latest text", inbox.get(0).getLastMessage());
            assertEquals("sender", inbox.get(0).getUsername());
            assertEquals(3L, inbox.get(0).getUnreadCount());
        }

        @Test
        @DisplayName("a room whose other member was deleted is skipped, not fatal")
        void testFindInbox_whenFriendDeleted_SkipsRoom() {
            ChatParticipant participant = new ChatParticipant(room.getId(), receiver.getUserId());
            when(participantRepository.findAllByUserId(receiver.getUserId())).thenReturn(List.of(participant));
            when(participantRepository.findAllByRoomId(room.getId()))
                    .thenReturn(List.of(participant, new ChatParticipant(room.getId(), "ghost")));
            when(gamerRepository.findById("ghost")).thenReturn(Optional.empty());
            ChatMessage last = storedMessage("ghost", "text");
            when(messageRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId()))
                    .thenReturn(Optional.of(last));

            var inbox =
                    chatMessageService.findInbox(receiver).getBody().getData().getInboxList();

            assertTrue(inbox.isEmpty(), "used to throw USER_NOT_FOUND for every other conversation too");
        }
    }

    @Nested
    class Report {

        @Test
        @DisplayName("reporting keeps the text: the moderator has to be able to read what was said")
        void testReportMessage_whenRecipient_PreservesTheEvidence() {
            ChatMessage message = storedMessage(sender.getUserId(), "something offensive");
            UUID messageId = message.getId();
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(participantRepository.findByRoomIdAndUserId(room.getId(), receiver.getUserId()))
                    .thenReturn(Optional.of(new ChatParticipant(room.getId(), receiver.getUserId())));

            DefaultMessageResponse response = chatMessageService.reportMessage(receiver, message.getId());

            assertEquals("100", response.getStatus().getCode());
            assertEquals(NOW, message.getReportedAt());
            // An earlier version overwrote the body with asterisks, destroying the only
            // copy and leaving the moderation screen showing nothing readable.
            assertEquals(
                    "something offensive",
                    cipher.decrypt(message.getBody(), message.getNonce(), message.getKeyVersion()));
        }

        @Test
        @DisplayName("the sender cannot report their own message into the moderation queue")
        void testReportMessage_whenSender_ReturnErrorCode143() {
            ChatMessage message = storedMessage(sender.getUserId(), "text");
            when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
            UUID id = message.getId();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> chatMessageService.reportMessage(sender, id));
            assertEquals(143, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("someone outside the conversation cannot report it")
        void testReportMessage_whenNotAParticipant_ReturnErrorCode143() {
            ChatMessage message = storedMessage(sender.getUserId(), "text");
            when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
            when(participantRepository.findByRoomIdAndUserId(room.getId(), stranger.getUserId()))
                    .thenReturn(Optional.empty());
            UUID id = message.getId();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> chatMessageService.reportMessage(stranger, id));
            assertEquals(143, ex.getTransactionCode().getId());
        }

        @Test
        void testReportMessage_whenMessageNotFound_ReturnErrorCode142() {
            UUID missing = UUID.randomUUID();
            when(messageRepository.findById(missing)).thenReturn(Optional.empty());

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> chatMessageService.reportMessage(receiver, missing));
            assertEquals(142, ex.getTransactionCode().getId());
        }
    }
}
