package com.gamebuddy.match.domain.service.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import com.gamebuddy.match.infrastructure.entity.ChatParticipant;
import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.match.infrastructure.repository.ChatParticipantRepository;
import com.gamebuddy.match.interfaces.dto.MessageForReport;
import com.gamebuddy.match.interfaces.dto.ReportedMessageDto;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.messaging.MessageCipher;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatModerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @InjectMocks
    private ChatModerationService moderationService;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatParticipantRepository participantRepository;

    @Mock
    private GamerRepository gamerRepository;

    /** Real, because decrypting for the moderator is the behaviour under test. */
    @Spy
    private MessageCipher cipher = new MessageCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final UUID room = UUID.randomUUID();
    private Gamer moderator;
    private Gamer sender;
    private Gamer receiver;

    @BeforeEach
    void setUp() {
        moderator = newGamer("mod@example.com", "moderator");
        sender = newGamer("sender@example.com", "sender");
        receiver = newGamer("receiver@example.com", "receiver");
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        return g;
    }

    private ChatMessage stored(String senderId, String text, Instant at) {
        MessageCipher.Encrypted encrypted = cipher.encrypt(text);
        ChatMessage m = new ChatMessage();
        m.setId(UUID.randomUUID());
        m.setRoomId(room);
        m.setSenderId(senderId);
        m.setBody(encrypted.ciphertext());
        m.setNonce(encrypted.nonce());
        m.setKeyVersion(MessageCipher.CURRENT_KEY_VERSION);
        m.setCreatedAt(at);
        return m;
    }

    private void receiverIsInTheRoom() {
        when(participantRepository.findByRoomIdAndUserId(room, receiver.getUserId()))
                .thenReturn(Optional.of(new ChatParticipant(room, receiver.getUserId())));
    }

    @Test
    @DisplayName("a report captures the messages either side, in order, with the reported one in the middle")
    void testPrepareReport_capturesContextInOrder() {
        ChatMessage earlier = stored(sender.getUserId(), "hi", NOW.minusSeconds(20));
        ChatMessage earlier2 = stored(receiver.getUserId(), "hey", NOW.minusSeconds(10));
        ChatMessage reported = stored(sender.getUserId(), "something offensive", NOW);
        ChatMessage later = stored(receiver.getUserId(), "wow", NOW.plusSeconds(5));
        when(messageRepository.findById(reported.getId())).thenReturn(Optional.of(reported));
        receiverIsInTheRoom();
        // Newest first, the way the repository hands them back.
        when(messageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        eq(room), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(earlier2, earlier));
        when(messageRepository.findByRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(eq(room), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(later));

        MessageForReport described = moderationService.prepareReport(reported.getId(), receiver.getUserId(), 10);

        assertEquals(sender.getUserId(), described.senderId());
        assertEquals(room, described.roomId());
        assertEquals(
                List.of(earlier.getId(), earlier2.getId(), reported.getId(), later.getId()), described.contextIds());
        assertEquals(NOW, reported.getReportedAt(), "the row itself says it was complained about");
        // The text is untouched: an earlier version overwrote the body with asterisks,
        // destroying the only copy and leaving the moderation screen nothing to read.
        assertEquals(
                "something offensive",
                cipher.decrypt(reported.getBody(), reported.getNonce(), reported.getKeyVersion()));
    }

    @Test
    @DisplayName("the sender cannot report their own message into the moderation queue")
    void testPrepareReport_whenSender_ReturnErrorCode143() {
        ChatMessage message = stored(sender.getUserId(), "text", NOW);
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        UUID id = message.getId();

        BusinessException ex = assertThrows(
                BusinessException.class, () -> moderationService.prepareReport(id, sender.getUserId(), 10));
        assertEquals(143, ex.getTransactionCode().getId());
    }

    @Test
    @DisplayName("someone outside the conversation cannot report it")
    void testPrepareReport_whenNotAParticipant_ReturnErrorCode143() {
        ChatMessage message = stored(sender.getUserId(), "text", NOW);
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(participantRepository.findByRoomIdAndUserId(eq(room), anyString())).thenReturn(Optional.empty());
        UUID id = message.getId();

        BusinessException ex =
                assertThrows(BusinessException.class, () -> moderationService.prepareReport(id, "stranger", 10));
        assertEquals(143, ex.getTransactionCode().getId());
    }

    @Test
    void testPrepareReport_whenMessageNotFound_ReturnErrorCode142() {
        UUID missing = UUID.randomUUID();
        when(messageRepository.findById(missing)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class, () -> moderationService.prepareReport(missing, receiver.getUserId(), 10));
        assertEquals(142, ex.getTransactionCode().getId());
    }

    @Test
    @DisplayName("the moderator's read decrypts: a moderator looking at ciphertext could not act on a report")
    void testReadForModerator_decryptsInTimeOrder() {
        ChatMessage second = stored(sender.getUserId(), "something offensive", NOW);
        ChatMessage first = stored(receiver.getUserId(), "hello", NOW.minusSeconds(30));
        when(messageRepository.findAllById(anyList())).thenReturn(List.of(second, first));
        when(gamerRepository.findAllById(anySet())).thenReturn(List.of(sender, receiver));

        List<ReportedMessageDto> read =
                moderationService.readForModerator(moderator, List.of(second.getId(), first.getId()));

        assertEquals(2, read.size());
        assertEquals("hello", read.get(0).message(), "time order, whatever order the ids arrived in");
        assertEquals("something offensive", read.get(1).message());
        assertEquals("sender", read.get(1).senderUsername());
    }

    @Test
    @DisplayName("a message from a since-deleted account is still reviewable")
    void testReadForModerator_whenSenderDeleted_StillReturnsIt() {
        ChatMessage message = stored("ghost", "text", NOW);
        when(messageRepository.findAllById(anyList())).thenReturn(List.of(message));
        when(gamerRepository.findAllById(anySet())).thenReturn(List.of());

        List<ReportedMessageDto> read = moderationService.readForModerator(moderator, List.of(message.getId()));

        assertEquals(1, read.size(), "deleting the account must not hide what was reported");
        assertNull(read.get(0).senderUsername());
        assertEquals("text", read.get(0).message());
    }

    @Test
    @DisplayName("senders are resolved in one query, not one per row")
    void testReadForModerator_resolvesSendersInOneQuery() {
        ChatMessage a = stored(sender.getUserId(), "one", NOW);
        ChatMessage b = stored(sender.getUserId(), "two", NOW.plusSeconds(1));
        when(messageRepository.findAllById(anyList())).thenReturn(List.of(a, b));
        when(gamerRepository.findAllById(anySet())).thenReturn(List.of(sender));

        moderationService.readForModerator(moderator, List.of(a.getId(), b.getId()));

        verify(gamerRepository, times(1)).findAllById(anySet());
        verify(gamerRepository, never()).findById(anyString());
    }

    @Test
    void testReadForModerator_withNoIds_ReadsNothing() {
        assertTrue(moderationService.readForModerator(moderator, List.of()).isEmpty());
        verify(messageRepository, never()).findAllById(anyList());
    }
}
