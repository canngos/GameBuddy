package com.gamebuddy.match.domain.service.chat;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.match.infrastructure.repository.ChatParticipantRepository;
import com.gamebuddy.match.interfaces.dto.MessageForReport;
import com.gamebuddy.match.interfaces.dto.ReportedMessageDto;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.messaging.MessageCipher;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat's side of a message report.
 *
 * <p>Lives with chat rather than with moderation. Reviewing a reported message means
 * reading chat, and reaching across a module boundary into another module's repository
 * is the coupling that let the same table be mapped two different ways elsewhere in this
 * codebase. Moderation files and decides the report; this is where it asks what was said.
 *
 * <p><strong>This is the one place plaintext leaves the conversation.</strong> Message
 * bodies are encrypted at rest and normally only decrypted for the two participants; in
 * {@link #readForModerator} they are decrypted for someone who was not part of the
 * exchange. That is the deliberate cost of not being end-to-end encrypted — without it a
 * report would show a moderator ciphertext and the whole reporting flow would be theatre.
 * Every such read is logged with the moderator's id, so the access is auditable.
 *
 * <p>The report itself carries ids, never text: see {@code ReportEvidence}. A second copy
 * of the plaintext in the report table would be a second, unaudited place to read private
 * conversations from.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModerationService {

    private final ChatMessageRepository messageRepository;
    private final ChatParticipantRepository participantRepository;
    private final GamerRepository gamerRepository;
    private final MessageCipher cipher;
    private final Clock clock;

    /**
     * Describes a message for the report somebody is filing about it.
     *
     * <p>Only the recipient may report. Letting the sender flag their own message would be
     * a way to fill the moderation queue with text they wrote themselves, and letting a
     * stranger do it would be a way to read a conversation they are not in.
     *
     * <p>The context is the message plus up to {@code contextMessages} either side, in
     * order. A single line is not evidence of anything: "come on then" is a threat in one
     * conversation and a game invitation in the next.
     *
     * <p>Stamps {@code reportedAt} on the message. Nothing reads it any more except a
     * database query by hand; it is kept because it costs nothing and says, on the row
     * itself, that this one was complained about.
     */
    @Transactional
    public MessageForReport prepareReport(UUID messageId, String reporterId, int contextMessages) {
        ChatMessage message = messageRepository
                .findById(messageId)
                .orElseThrow(() -> new BusinessException(TransactionCode.MESSAGE_NOT_FOUND));

        if (message.getSenderId().equals(reporterId)) {
            throw new BusinessException(TransactionCode.RECEIVER_IS_DIFFERENT);
        }
        if (participantRepository
                .findByRoomIdAndUserId(message.getRoomId(), reporterId)
                .isEmpty()) {
            throw new BusinessException(TransactionCode.RECEIVER_IS_DIFFERENT);
        }

        if (message.getReportedAt() == null) {
            message.setReportedAt(clock.instant());
            messageRepository.save(message);
        }

        List<ChatMessage> before = messageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                message.getRoomId(), message.getCreatedAt(), PageRequest.of(0, contextMessages));
        List<ChatMessage> after = messageRepository.findByRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(
                message.getRoomId(), message.getCreatedAt(), PageRequest.of(0, contextMessages));

        List<UUID> context = new ArrayList<>(before.size() + 1 + after.size());
        for (int i = before.size() - 1; i >= 0; i--) {
            context.add(before.get(i).getId());
        }
        context.add(message.getId());
        after.forEach(m -> context.add(m.getId()));

        return new MessageForReport(message.getId(), message.getRoomId(), message.getSenderId(), context);
    }

    /**
     * The messages a report captured, decrypted for the person deciding it.
     *
     * <p>Only ever by id, and only the ids a report recorded — there is no "read this
     * room" here. A moderator sees the window the reporter's complaint was about, which
     * is enough to judge it and no more than the reporter themselves could see.
     *
     * <p>Ordered by time regardless of the order the ids arrive in, and a message that has
     * since vanished is simply absent rather than an error: a deleted account takes
     * nothing with it today, but the read path should not depend on that staying true.
     */
    @Transactional(readOnly = true)
    public List<ReportedMessageDto> readForModerator(Gamer moderator, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>(messageRepository.findAllById(ids));
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId));

        // Deliberate and auditable: a named person read private messages they were not
        // party to. If this line is ever noisy, that is worth knowing too.
        log.info("Moderator {} read {} message(s) attached to a report", moderator.getUserId(), messages.size());

        // One query for the senders rather than one per row.
        Set<String> senderIds = messages.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Map<String, Gamer> senders = gamerRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(Gamer::getUserId, Function.identity()));

        return messages.stream()
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
    }
}
