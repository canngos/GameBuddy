package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.lobby.application.mapper.LobbyMapper;
import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMessage;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
import com.gamebuddy.lobby.interfaces.dto.LobbyEvent;
import com.gamebuddy.lobby.interfaces.dto.LobbyMessageDto;
import com.gamebuddy.lobby.interfaces.dto.LobbyMessageResponseBody;
import com.gamebuddy.lobby.interfaces.dto.LobbyMessagesResponseBody;
import com.gamebuddy.lobby.interfaces.response.LobbyMessageResponse;
import com.gamebuddy.lobby.interfaces.response.LobbyMessagesResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.messaging.MessageCipher;
import com.gamebuddy.shared.messaging.UserMessaging;
import com.gamebuddy.shared.moderation.TextAssessment;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.moderation.TextSurface;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.common.util.Constants;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lobby's group chat, borrowing everything 1:1 chat proved out.
 *
 * <p>Same at-rest encryption ({@code MessageCipher}), same read-watermark shape, same
 * HTTP-send / socket-receive posture, same in-transaction notification event. Different
 * preconditions: membership is "the owner accepted you", not "you matched", and the
 * screen is {@code PRIVATE} — everyone in the room was chosen by hand, and swapping
 * Discord tags in a team chat is this feature working, not a leak.
 *
 * <p>Chat outlives the game read-only: ENDED and CANCELLED lobbies still answer history
 * ("what was that guy's Discord again?") but refuse new lines. ARCHIVED is gone entirely,
 * messages deleted with it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLobbyChatService implements LobbyChatService {

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository memberRepository;
    private final LobbyMessageRepository messageRepository;
    private final GamerRepository gamerRepository;
    private final LobbyMapper mapper;
    private final MessageCipher cipher;
    private final TextModerationService textModeration;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final UserMessaging messaging;
    private final RateLimiter lobbyMessageRateLimiter;

    @Override
    @Transactional
    public LobbyMessagesResponse messages(Gamer principal, UUID lobbyId) {
        Gamer gamer = requireGamer(principal.getUserId());
        requireVisibleLobby(lobbyId);
        LobbyMember me = requireTeamMember(lobbyId, gamer.getUserId());

        // Reading is what moves the watermark, same as 1:1 chat.
        me.setLastReadAt(clock.instant());
        memberRepository.save(me);

        List<LobbyMessage> history = messageRepository.findAllByLobbyIdOrderByCreatedAtAsc(lobbyId);
        Map<String, Gamer> senders = gamerRepository
                .findAllById(history.stream().map(LobbyMessage::getSenderId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Gamer::getUserId, Function.identity()));

        List<LobbyMessageDto> messages = history.stream()
                .map(m -> mapper.toMessageDto(
                        m.getId(),
                        m.getSenderId(),
                        senders.containsKey(m.getSenderId())
                                ? senders.get(m.getSenderId()).getGamerUsername()
                                : null,
                        cipher.decrypt(m.getBody(), m.getNonce(), m.getKeyVersion()),
                        m.getCreatedAt()))
                .toList();

        LobbyMessagesResponseBody body = new LobbyMessagesResponseBody();
        body.setMessages(messages);
        return respond(new LobbyMessagesResponse(), body);
    }

    @Override
    @Transactional
    public LobbyMessageResponse send(Gamer principal, UUID lobbyId, String text) {
        Gamer sender = requireGamer(principal.getUserId());
        Lobby lobby = requireVisibleLobby(lobbyId);
        requireTeamMember(lobbyId, sender.getUserId());

        // Read-only once the lobby is over. LOCKED still chats — that is the whole point
        // of locking without a timer: the team talks until they play.
        if (lobby.getStatus() != LobbyStatus.OPEN && lobby.getStatus() != LobbyStatus.LOCKED) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }
        if (!lobbyMessageRateLimiter.tryAcquire(sender.getUserId())) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        // Screened before encryption, because after it nothing can read the text — including
        // a filter. PRIVATE surface: profanity masked, slurs refused, contact details left
        // alone.
        TextAssessment assessment = textModeration.screen(text, TextSurface.PRIVATE);
        if (assessment.blocked()) {
            throw new BusinessException(TransactionCode.CONTENT_BLOCKED);
        }
        String screened = assessment.cleaned();

        MessageCipher.Encrypted encrypted = cipher.encrypt(screened);
        LobbyMessage message = new LobbyMessage();
        message.setId(UUID.randomUUID());
        message.setLobbyId(lobbyId);
        message.setSenderId(sender.getUserId());
        message.setBody(encrypted.ciphertext());
        message.setNonce(encrypted.nonce());
        message.setKeyVersion(MessageCipher.CURRENT_KEY_VERSION);
        message.setCreatedAt(clock.instant());
        messageRepository.save(message);

        LobbyMessageDto dto = mapper.toMessageDto(
                message.getId(), sender.getUserId(), sender.getGamerUsername(), screened, message.getCreatedAt());

        // Everybody on the team but the author: one socket frame and one queued push each.
        // The frame carries the line itself; the push is for phones in pockets. Fan-out is
        // keyed on the email — the STOMP principal — because a userId delivers to nobody,
        // silently.
        List<String> teamIds = memberRepository.findAllByLobbyId(lobbyId).stream()
                .filter(m -> m.getStatus().inTeam())
                .map(LobbyMember::getUserId)
                .filter(id -> !id.equals(sender.getUserId()))
                .toList();
        LobbyEvent event = LobbyEvent.messageEvent(lobbyId.toString(), dto);
        gamerRepository.findAllById(teamIds).forEach(member -> {
            messaging.sendToUser(member.getEmail(), "/queue/lobby", event);
            events.publishEvent(new NotificationRequestedEvent(
                    member.getUserId(),
                    member.getFcmToken(),
                    sender.getGamerUsername(),
                    preview(screened),
                    NotificationKind.LOBBY_MESSAGE,
                    lobbyId.toString()));
        });

        // The screened text goes back to the author, so their client renders the same
        // words everybody else received.
        LobbyMessageResponseBody body = new LobbyMessageResponseBody();
        body.setMessage(dto);
        return respond(new LobbyMessageResponse(), body);
    }

    // ------------------------------------------------------------------------

    private Lobby requireVisibleLobby(UUID lobbyId) {
        return lobbyRepository
                .findById(lobbyId)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.ARCHIVED)
                .orElseThrow(() -> new BusinessException(TransactionCode.LOBBY_NOT_FOUND));
    }

    private LobbyMember requireTeamMember(UUID lobbyId, String userId) {
        return memberRepository
                .findByLobbyIdAndUserId(lobbyId, userId)
                .filter(m -> m.getStatus().inTeam())
                .orElseThrow(() -> new BusinessException(TransactionCode.LOBBY_NOT_MEMBER));
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

    private <T extends BaseModel, R extends BaseResponse<T>> R respond(R response, T body) {
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
