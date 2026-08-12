package com.gamebuddy.match.domain.service.chat;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.AgeBand;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import com.gamebuddy.match.infrastructure.entity.ChatParticipant;
import com.gamebuddy.match.infrastructure.entity.ChatRoom;
import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.match.infrastructure.repository.ChatParticipantRepository;
import com.gamebuddy.match.interfaces.dto.ConversationDto;
import com.gamebuddy.match.interfaces.dto.ConversationResponseBody;
import com.gamebuddy.match.interfaces.dto.InboxDto;
import com.gamebuddy.match.interfaces.dto.InboxResponseBody;
import com.gamebuddy.match.interfaces.dto.PresenceResponseBody;
import com.gamebuddy.match.interfaces.dto.PresenceUpdate;
import com.gamebuddy.match.interfaces.dto.TypingNotification;
import com.gamebuddy.match.interfaces.response.ConversationResponse;
import com.gamebuddy.match.interfaces.response.InboxResponse;
import com.gamebuddy.match.interfaces.response.PresenceResponse;
import com.gamebuddy.shared.entity.Avatars;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.moderation.TextAssessment;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.moderation.TextSurface;
import com.gamebuddy.shared.repository.AvatarsRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat, on Postgres.
 *
 * <p>Moved off MongoDB because the two documents it held were entirely relational — every
 * field scalar, both indexes translating one for one — so the second datastore bought
 * nothing and cost a whole managed service in any deployment.
 *
 * <p>Message bodies are encrypted before they are written; see {@link MessageCipher}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository messageRepository;
    private final ChatParticipantRepository participantRepository;
    private final GamerRepository gamerRepository;
    private final AvatarsRepository avatarsRepository;
    private final AvatarUrls avatarUrls;
    private final ChatRoomService chatRoomService;
    private final MessageCipher cipher;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final PresenceService presenceService;
    private final TextModerationService textModeration;
    private final UserMessaging messaging;

    /**
     * How many messages have ever been sent, for the console's dashboard.
     *
     * <p>Exposed here rather than letting the console read {@code chat_message}, which this
     * module owns. A count and nothing else: the console has no business being able to
     * reach message bodies, and this is the shape that makes that true by construction
     * rather than by everyone remembering.
     */
    @Transactional(readOnly = true)
    public long messageCount() {
        return messageRepository.count();
    }

    /**
     * Whether somebody a gamer has matched with is online.
     *
     * <p>The match check is the point, not a formality. Presence tells you when a person is
     * awake and holding their phone, so it is disclosed only to people they have agreed to
     * talk to — the same bar chat itself sets. Asking about a stranger is refused rather
     * than answered with "offline", which would still confirm the account exists.
     */
    @Transactional(readOnly = true)
    public PresenceResponse presenceOf(Gamer principal, String userId) {
        Gamer self = requireGamer(principal.getUserId());
        Gamer other = requireGamer(userId);
        if (!self.isMatchedWith(other)) {
            throw new BusinessException(TransactionCode.NOT_MATCHED);
        }

        PresenceUpdate presence = presenceService.presenceOf(userId);
        PresenceResponseBody body = new PresenceResponseBody();
        body.setUserId(presence.userId());
        body.setOnline(presence.online());
        body.setLastSeenAt(presence.lastSeenAt());
        return respond(new PresenceResponse(), body);
    }

    /**
     * Passes a typing indicator to the recipient, if they are entitled to it.
     *
     * <p>Re-checks the match on every event rather than trusting that the conversation was
     * opened legitimately, because the socket is a public destination and nothing stops a
     * client sending this for an id it invented. A block or an age change also takes effect
     * immediately, exactly as it does for messages.
     *
     * <p>Nothing is stored and nothing is queued for later. If the recipient is not
     * connected the send is a no-op, which is correct: there is no such thing as a typing
     * indicator you missed.
     */
    @Transactional(readOnly = true)
    public void relayTyping(String senderId, String receiverId) {
        if (senderId.equals(receiverId)) {
            return;
        }
        Gamer sender = requireGamer(senderId);
        Gamer receiver = requireGamer(receiverId);

        if (!sender.isMatchedWith(receiver)
                || sender.hasBlockRelationshipWith(receiver)
                || !AgeBand.compatible(sender.getAge(), receiver.getAge())) {
            return;
        }

        messaging.sendToUser(receiver.getEmail(), "/queue/typing", new TypingNotification(senderId));
    }

    /**
     * Stores an inbound chat message.
     *
     * <p>{@code senderId} comes from the authenticated STOMP session, never from the
     * payload — see {@link com.gamebuddy.match.config.StompAuthChannelInterceptor}. The
     * timestamp is assigned here too; it used to arrive from the client and was never set
     * server side at all.
     */
    @Transactional
    public SentMessage save(String senderId, String receiverId, String body) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot message yourself");
        }
        Gamer sender = requireGamer(senderId);
        Gamer receiver = requireGamer(receiverId);

        // Chat is for people who have matched, and the match must still be valid. All
        // three of these were unchecked: any account could message any other, a block did
        // not stop an existing conversation, and a minor and an adult could be paired.
        // Re-checked on every message rather than only at match time, so a block or an age
        // change takes effect immediately on conversations already open.
        if (!sender.isMatchedWith(receiver)) {
            throw new BusinessException(TransactionCode.NOT_MATCHED);
        }
        if (sender.hasBlockRelationshipWith(receiver)) {
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
        if (!AgeBand.compatible(sender.getAge(), receiver.getAge())) {
            throw new BusinessException(TransactionCode.AGE_BAND_MISMATCH);
        }

        // Screened before it is encrypted, because after encryption nothing can read it —
        // including a filter. PRIVATE: profanity is masked and slurs are refused, but
        // contact details are left alone. Two matched adults agreeing to carry on in a
        // party chat is this app working, not a leak to be plugged.
        TextAssessment assessment = textModeration.screen(body, TextSurface.PRIVATE);
        if (assessment.blocked()) {
            throw new BusinessException(TransactionCode.CONTENT_BLOCKED);
        }
        String screened = assessment.cleaned();

        ChatRoom room = chatRoomService.findOrCreate(senderId, receiverId);
        MessageCipher.Encrypted encrypted = cipher.encrypt(screened);

        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setRoomId(room.getId());
        message.setSenderId(senderId);
        message.setBody(encrypted.ciphertext());
        message.setNonce(encrypted.nonce());
        message.setKeyVersion(MessageCipher.CURRENT_KEY_VERSION);
        message.setCreatedAt(clock.instant());
        messageRepository.save(message);

        // The push. Queued inside this transaction like every other notification, so a
        // message that fails to store cannot announce itself.
        //
        // Sent unconditionally, including when the recipient has the conversation open:
        // the server cannot know what is on their screen, and the client suppresses the
        // banner for the chat it is already showing. Guessing here would mean a message
        // silently not notifying because a socket happened to be connected.
        //
        // The title is the sender and the body is what they said. That is what makes the
        // notification worth having — and it is also a lock-screen preview, which is the
        // same trade every chat app makes and worth revisiting if this ever carries
        // anything more sensitive than chat.
        events.publishEvent(new NotificationRequestedEvent(
                receiver.getUserId(),
                receiver.getFcmToken(),
                sender.getGamerUsername(),
                preview(screened),
                NotificationKind.MESSAGE,
                senderId));

        // The screened text, not what was typed. The sender's own client renders what it
        // gets back, so returning the original would show the author their unmasked words
        // while everybody else saw asterisks — and they would reasonably conclude the
        // filter had not fired.
        return new SentMessage(
                message.getId(),
                senderId,
                sender.getGamerUsername(),
                screened,
                message.getCreatedAt(),
                receiver.getEmail());
    }

    /**
     * A conversation, oldest first, and marks it read.
     *
     * <p>Reading the conversation is what moves the watermark. The Mongo version wrote a
     * status onto every individual message instead, so opening a fifty message thread
     * issued fifty row updates and fifty index updates.
     */
    @Transactional
    public ConversationResponse findChatMessages(Gamer principal, String friendId) {
        Gamer gamer = requireGamer(principal.getUserId());
        Optional<ChatRoom> room = chatRoomService.find(gamer.getUserId(), friendId);

        List<ConversationDto> conversations = room.map(r -> {
                    markRead(r.getId(), gamer.getUserId());
                    return messageRepository.findByRoomIdOrderByCreatedAtAsc(r.getId()).stream()
                            .map(message -> toConversationDto(message, gamer.getUserId(), friendId))
                            .toList();
                })
                .orElseGet(List::of);

        ConversationResponseBody body = new ConversationResponseBody();
        body.setConversations(conversations);
        return respond(new ConversationResponse(), body);
    }

    @Transactional(readOnly = true)
    public InboxResponse findInbox(Gamer principal) {
        Gamer gamer = requireGamer(principal.getUserId());

        List<InboxDto> inbox = participantRepository.findAllByUserId(gamer.getUserId()).stream()
                .map(participant -> toInboxRow(participant, gamer))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(InboxDto::getLastMessageTime).reversed())
                .toList();

        InboxResponseBody body = new InboxResponseBody();
        body.setInboxList(inbox);
        return respond(new InboxResponse(), body);
    }

    /**
     * Flags a message for moderation.
     *
     * <p>An earlier version also overwrote the body with asterisks. That destroyed the only
     * copy of the text: the entire point of a report is for a moderator to read what was
     * said, so the moderation screen showed a row of asterisks and nothing else. The text
     * is kept; hiding it from the reporter is a client concern.
     */
    @Transactional
    public DefaultMessageResponse reportMessage(Gamer principal, UUID messageId) {
        ChatMessage message = messageRepository
                .findById(messageId)
                .orElseThrow(() -> new BusinessException(TransactionCode.MESSAGE_NOT_FOUND));

        // Only the recipient may report. Letting the sender flag their own message would
        // be a way to fill the moderation queue with text they wrote themselves.
        if (message.getSenderId().equals(principal.getUserId())) {
            throw new BusinessException(TransactionCode.RECEIVER_IS_DIFFERENT);
        }
        if (participantRepository
                .findByRoomIdAndUserId(message.getRoomId(), principal.getUserId())
                .isEmpty()) {
            throw new BusinessException(TransactionCode.RECEIVER_IS_DIFFERENT);
        }

        message.setReportedAt(clock.instant());
        messageRepository.save(message);
        return DefaultMessageResponse.of("Message reported successfully");
    }

    /** Moves this gamer's read watermark to now. */
    @Transactional
    public void markRead(UUID roomId, String userId) {
        participantRepository.findByRoomIdAndUserId(roomId, userId).ifPresent(participant -> {
            participant.setLastReadAt(clock.instant());
            participantRepository.save(participant);
        });
    }

    // ------------------------------------------------------------------------

    private ConversationDto toConversationDto(ChatMessage message, String selfId, String friendId) {
        ConversationDto dto = new ConversationDto();
        dto.setId(message.getId().toString());
        dto.setSender(message.getSenderId());
        // Derived rather than stored: a one to one room has exactly one other participant,
        // so a receiver column would be a second copy of something already known.
        dto.setReceiver(message.getSenderId().equals(selfId) ? friendId : selfId);
        dto.setMessage(cipher.decrypt(message.getBody(), message.getNonce(), message.getKeyVersion()));
        dto.setDate(message.getCreatedAt());
        return dto;
    }

    /**
     * One inbox row, or empty when the room cannot produce one.
     *
     * <p>Both skip conditions used to be absent: an empty room made the last message null
     * and the resulting NPE took the whole inbox down, and a room pointing at a deleted
     * gamer threw USER_NOT_FOUND for every other conversation as well.
     */
    private Optional<InboxDto> toInboxRow(ChatParticipant participant, Gamer gamer) {
        Optional<ChatMessage> lastMessage =
                messageRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(participant.getRoomId());
        if (lastMessage.isEmpty()) {
            // A room exists as soon as either side opens the conversation, so an empty one
            // is normal rather than an error.
            return Optional.empty();
        }

        Optional<Gamer> friend = otherParticipant(participant.getRoomId(), gamer.getUserId());
        if (friend.isEmpty()) {
            log.warn("Chat room {} references a gamer that no longer exists", participant.getRoomId());
            return Optional.empty();
        }

        ChatMessage last = lastMessage.get();
        InboxDto dto = new InboxDto();
        dto.setUserId(friend.get().getUserId());
        dto.setUsername(friend.get().getGamerUsername());
        dto.setAvatar(avatarUrls.visibleTo(friend.get()));
        dto.setLastMessage(cipher.decrypt(last.getBody(), last.getNonce(), last.getKeyVersion()));
        dto.setLastMessageTime(last.getCreatedAt());
        // A conversation never opened has no watermark, which means everything is unread.
        // Translated here rather than in the query: a bare parameter compared only against
        // NULL leaves Postgres unable to infer its type.
        Instant since = participant.getLastReadAt() == null ? Instant.EPOCH : participant.getLastReadAt();
        dto.setUnreadCount(messageRepository.countUnread(participant.getRoomId(), gamer.getUserId(), since));
        return Optional.of(dto);
    }

    private Optional<Gamer> otherParticipant(UUID roomId, String selfId) {
        return participantRepository.findAllByRoomId(roomId).stream()
                .filter(p -> !p.getUserId().equals(selfId))
                .findFirst()
                .flatMap(p -> gamerRepository.findById(p.getUserId()));
    }

    /** A short lead-in, so a long message does not become a wall of text on a lock screen. */
    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return Constants.MESSAGE_BODY_FALLBACK;
        }
        String trimmed = text.strip();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 119) + "…";
    }

    private Gamer requireGamer(String userId) {
        return gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    private String resolveAvatar(UUID avatarId) {
        return avatarId == null
                ? null
                : avatarsRepository.findById(avatarId).map(Avatars::getImage).orElse(null);
    }

    private <T extends BaseModel, R extends BaseResponse<T>> R respond(R response, T body) {
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
