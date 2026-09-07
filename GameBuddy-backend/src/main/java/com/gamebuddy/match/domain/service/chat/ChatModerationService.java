package com.gamebuddy.match.domain.service.chat;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.match.interfaces.dto.ReportedMessageDto;
import com.gamebuddy.match.interfaces.dto.ReportedMessagesResponseBody;
import com.gamebuddy.match.interfaces.response.ReportedMessagesResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.messaging.MessageCipher;
import com.gamebuddy.shared.repository.GamerRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The moderation queue for reported chat.
 *
 * <p>Lives with chat rather than with the admin endpoints in the auth module. Reviewing a
 * reported message means reading chat, and reaching across a module boundary into another
 * module's repository is the coupling that let the same table be mapped two different ways
 * elsewhere in this codebase. The admin controller calls this service instead.
 *
 * <p><strong>This is the one place plaintext leaves the conversation.</strong> Message
 * bodies are encrypted at rest and normally only decrypted for the two participants; here
 * they are decrypted for someone who was not part of the exchange. That is the deliberate
 * cost of not being end-to-end encrypted — without it a report would show a moderator
 * ciphertext and the whole reporting flow would be theatre. Every call is logged with the
 * moderator's id so the access is auditable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModerationService {

    /** One screen's worth. A moderation queue is worked through, not scrolled forever. */
    private static final int QUEUE_PAGE_SIZE = 100;

    private final ChatMessageRepository messageRepository;
    private final GamerRepository gamerRepository;
    private final MessageCipher cipher;

    /** Everything awaiting review, newest report first. */
    @Transactional(readOnly = true)
    public ReportedMessagesResponse getReportedMessages(Gamer moderator) {
        Pageable page = PageRequest.of(0, QUEUE_PAGE_SIZE);
        List<ChatMessage> reported = messageRepository.findByReportedAtIsNotNullOrderByReportedAtDesc(page);

        // Deliberate and auditable: a named person read private messages they were not
        // party to. If this line is ever noisy, that is worth knowing too.
        log.info("Moderator {} opened the reported-message queue ({} entries)", moderator.getUserId(), reported.size());

        // One query for the senders rather than one per row.
        Set<String> senderIds = reported.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Map<String, Gamer> senders = gamerRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(Gamer::getUserId, Function.identity()));

        List<ReportedMessageDto> dtos = reported.stream()
                .map(message -> new ReportedMessageDto(
                        message.getId().toString(),
                        message.getRoomId().toString(),
                        message.getSenderId(),
                        senders.containsKey(message.getSenderId())
                                ? senders.get(message.getSenderId()).getGamerUsername()
                                // A deleted account still has messages worth reviewing.
                                : null,
                        cipher.decrypt(message.getBody(), message.getNonce(), message.getKeyVersion()),
                        message.getCreatedAt(),
                        message.getReportedAt()))
                .toList();

        ReportedMessagesResponse response = new ReportedMessagesResponse();
        response.setBody(new BaseBody<>(new ReportedMessagesResponseBody(dtos)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * Clears a report, leaving the message in place.
     *
     * <p>Dismissing a report is not deleting the message. The two are different decisions
     * — "this was fine" versus "this has to go" — and conflating them means a moderator
     * cannot mark something reviewed without destroying the evidence for the next report
     * against the same person.
     */
    @Transactional
    public DefaultMessageResponse dismissReport(Gamer moderator, UUID messageId) {
        ChatMessage message = messageRepository
                .findById(messageId)
                .orElseThrow(() -> new BusinessException(TransactionCode.MESSAGE_NOT_FOUND));

        if (message.getReportedAt() == null) {
            throw new BusinessException(TransactionCode.MESSAGE_NOT_REPORTED);
        }

        message.setReportedAt(null);
        messageRepository.save(message);
        log.info("Moderator {} dismissed the report on message {}", moderator.getUserId(), messageId);
        return DefaultMessageResponse.of("Report dismissed");
    }
}
