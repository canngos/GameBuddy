package com.gamebuddy.match.domain.service.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.match.interfaces.dto.ReportedMessageDto;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.messaging.MessageCipher;
import java.time.Instant;
import java.util.List;
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
    private GamerRepository gamerRepository;

    /** Real, because decrypting for the moderator is the behaviour under test. */
    @Spy
    private MessageCipher cipher = new MessageCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    private Gamer moderator;
    private Gamer sender;

    @BeforeEach
    void setUp() {
        moderator = newGamer("mod@example.com", "moderator");
        sender = newGamer("sender@example.com", "sender");
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        return g;
    }

    private ChatMessage reported(String senderId, String text) {
        MessageCipher.Encrypted encrypted = cipher.encrypt(text);
        ChatMessage m = new ChatMessage();
        m.setId(UUID.randomUUID());
        m.setRoomId(UUID.randomUUID());
        m.setSenderId(senderId);
        m.setBody(encrypted.ciphertext());
        m.setNonce(encrypted.nonce());
        m.setKeyVersion(MessageCipher.CURRENT_KEY_VERSION);
        m.setCreatedAt(NOW);
        m.setReportedAt(NOW);
        return m;
    }

    @Test
    @DisplayName("the queue decrypts: a moderator looking at ciphertext could not act on a report")
    void testGetReportedMessages_decryptsForReview() {
        ChatMessage message = reported(sender.getUserId(), "something offensive");
        when(messageRepository.findByReportedAtIsNotNullOrderByReportedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(message));
        when(gamerRepository.findAllById(anySet())).thenReturn(List.of(sender));

        List<ReportedMessageDto> queue = moderationService
                .getReportedMessages(moderator)
                .getBody()
                .getData()
                .getReportedMessages();

        assertEquals(1, queue.size());
        assertEquals("something offensive", queue.get(0).message());
        assertEquals("sender", queue.get(0).senderUsername());
        assertEquals(NOW, queue.get(0).reportedAt());
    }

    @Test
    @DisplayName("a message from a since-deleted account is still reviewable")
    void testGetReportedMessages_whenSenderDeleted_StillListsIt() {
        ChatMessage message = reported("ghost", "text");
        when(messageRepository.findByReportedAtIsNotNullOrderByReportedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(message));
        when(gamerRepository.findAllById(anySet())).thenReturn(List.of());

        List<ReportedMessageDto> queue = moderationService
                .getReportedMessages(moderator)
                .getBody()
                .getData()
                .getReportedMessages();

        assertEquals(1, queue.size(), "deleting the account must not hide what was reported");
        assertNull(queue.get(0).senderUsername());
        assertEquals("text", queue.get(0).message());
    }

    @Test
    @DisplayName("senders are resolved in one query, not one per row")
    void testGetReportedMessages_resolvesSendersInOneQuery() {
        ChatMessage a = reported(sender.getUserId(), "one");
        ChatMessage b = reported(sender.getUserId(), "two");
        when(messageRepository.findByReportedAtIsNotNullOrderByReportedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(a, b));
        when(gamerRepository.findAllById(anySet())).thenReturn(List.of(sender));

        moderationService.getReportedMessages(moderator);

        verify(gamerRepository, times(1)).findAllById(anySet());
        verify(gamerRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("dismissing clears the report but keeps the message")
    void testDismissReport_keepsTheMessage() {
        ChatMessage message = reported(sender.getUserId(), "borderline");
        UUID id = message.getId();
        when(messageRepository.findById(id)).thenReturn(java.util.Optional.of(message));

        moderationService.dismissReport(moderator, id);

        assertNull(message.getReportedAt(), "no longer in the queue");
        // Dismissing a report and deleting a message are different decisions. Conflating
        // them means a moderator cannot mark something reviewed without destroying the
        // evidence for the next report against the same person.
        assertNotNull(message.getBody(), "the message itself survives");
        verify(messageRepository).save(message);
        verify(messageRepository, never()).delete(any());
    }

    @Test
    void testDismissReport_whenNotReported_ReturnErrorCode141() {
        ChatMessage message = reported(sender.getUserId(), "text");
        message.setReportedAt(null);
        UUID id = message.getId();
        when(messageRepository.findById(id)).thenReturn(java.util.Optional.of(message));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> moderationService.dismissReport(moderator, id));
        assertEquals(141, ex.getTransactionCode().getId());
    }

    @Test
    void testDismissReport_whenMessageNotFound_ReturnErrorCode142() {
        UUID missing = UUID.randomUUID();
        when(messageRepository.findById(missing)).thenReturn(java.util.Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> moderationService.dismissReport(moderator, missing));
        assertEquals(142, ex.getTransactionCode().getId());
    }
}
