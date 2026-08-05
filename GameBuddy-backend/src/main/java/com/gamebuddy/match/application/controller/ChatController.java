package com.gamebuddy.match.application.controller;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.match.domain.service.chat.ChatMessageService;
import com.gamebuddy.match.domain.service.chat.SentMessage;
import com.gamebuddy.match.interfaces.dto.ChatNotification;
import com.gamebuddy.match.interfaces.request.ChatMessageRequest;
import com.gamebuddy.match.interfaces.response.ConversationResponse;
import com.gamebuddy.match.interfaces.response.InboxResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;

    /**
     * Receives a chat message over STOMP.
     *
     * <p>The sender is the authenticated principal of the socket session, established by
     * {@code StompAuthChannelInterceptor} on CONNECT. It used to be
     * {@code message.getSender()} — a field of the client's own JSON payload, on an
     * endpoint that required no authentication at all, so anyone able to open a socket
     * could post a message as any user to any user.
     */
    @MessageMapping("/chat")
    public void processMessage(Principal principal, @Payload @Valid ChatMessageRequest request) {
        deliver(chatMessageService.save(senderOf(principal), request.getReceiver(), request.getMessage()));
    }

    /**
     * Sends a chat message over HTTP.
     *
     * <p>The socket is not the system of record and never was: {@code save} writes the
     * message before anything is delivered, and {@code convertAndSendToUser} is a no-op
     * when the recipient is not connected — they read it from the database next time they
     * open the conversation. Being offline has therefore always been fine <em>for the
     * recipient</em>.
     *
     * <p>What was missing is the other half. STOMP was the only way in, so a
     * <em>sender</em> whose socket was down could not send at all, and a dropped socket
     * meant a dead compose box rather than a slow one. Sending over HTTP is an ordinary
     * request: it succeeds on any working connection, it can be retried, and it reports a
     * real error instead of failing silently into a closed channel.
     *
     * <p>The socket keeps its job — delivering to whoever is connected right now — and
     * both paths run the same {@code save} so the rules cannot diverge.
     */
    @PostMapping("/messages/send")
    @ResponseBody
    public ResponseEntity<DefaultMessageResponse> sendMessage(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChatMessageRequest request) {
        deliver(chatMessageService.save(principal.getUserId(), request.getReceiver(), request.getMessage()));
        return ResponseEntity.ok(DefaultMessageResponse.of("Message sent"));
    }

    /**
     * Pushes a stored message to the recipient's socket, if they have one.
     *
     * <p>Best effort by design. The message is already persisted before this runs, so a
     * recipient who is offline loses nothing.
     */
    private void deliver(SentMessage sent) {
        messagingTemplate.convertAndSendToUser(
                sent.receiverPrincipalName(),
                "/queue/messages",
                new ChatNotification(sent.id().toString(), sent.senderId(), sent.senderName(), sent.body()));
    }

    /**
     * The authenticated sender of a socket message.
     *
     * <p>Read from {@link Principal} rather than through {@code @AuthenticationPrincipal},
     * which does not work here and fails in the worst possible way. That annotation is
     * resolved by {@code spring-security-messaging}, which is not on the classpath; without
     * it Spring Messaging falls back to its catch-all payload resolver and binds the
     * argument <em>from the client's own JSON</em>. A {@code Gamer} parameter was therefore
     * being deserialised out of the message body, so sending
     * {@code {"userId":"someone-else", ...}} stored the message as that person — the exact
     * impersonation the CONNECT-time authentication exists to prevent.
     *
     * <p>{@link Principal} is resolved by Spring Messaging itself and needs no extra
     * dependency, so this cannot silently fall through again. An unauthenticated frame is
     * refused rather than defaulted.
     */
    private String senderOf(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Gamer gamer) {
            return gamer.getUserId();
        }
        // StompAuthChannelInterceptor rejects a CONNECT without a valid token, so reaching
        // here means the wiring changed underneath us. Fail rather than guess.
        log.error("Chat message arrived with no authenticated principal: {}", principal);
        throw new BusinessException(TransactionCode.TOKEN_INVALID);
    }

    @GetMapping("/messages/get/{friendId}")
    public ResponseEntity<ConversationResponse> findChatMessages(
            @AuthenticationPrincipal Gamer principal, @PathVariable String friendId) {
        return ResponseEntity.ok(chatMessageService.findChatMessages(principal, friendId));
    }

    @GetMapping("/messages/get/inbox")
    public ResponseEntity<InboxResponse> findInbox(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(chatMessageService.findInbox(principal));
    }

    @PostMapping("/messages/report/{messageId}")
    public ResponseEntity<DefaultMessageResponse> reportMessage(
            @AuthenticationPrincipal Gamer principal, @PathVariable String messageId) {
        // Parsed here so a malformed id is a 400 rather than a 500 out of the repository.
        return ResponseEntity.ok(chatMessageService.reportMessage(principal, Ids.uuid(messageId)));
    }
}
