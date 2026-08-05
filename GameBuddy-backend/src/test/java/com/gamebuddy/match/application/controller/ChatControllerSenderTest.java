package com.gamebuddy.match.application.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.match.domain.service.chat.ChatMessageService;
import com.gamebuddy.match.domain.service.chat.SentMessage;
import com.gamebuddy.match.interfaces.request.ChatMessageRequest;
import com.gamebuddy.shared.entity.Gamer;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * The sender of a chat message comes from the socket session, never from the payload.
 *
 * <p>This is not a hypothetical. The controller originally declared
 * {@code @AuthenticationPrincipal Gamer principal}, which is resolved by
 * spring-security-messaging — a library that is not on the classpath. Spring Messaging then
 * fell back to its catch-all payload resolver and deserialised the argument from the
 * client's own JSON, so sending {@code {"userId":"someone-else", ...}} stored the message
 * as that person. A live socket test confirmed the impersonation before it was fixed.
 *
 * <p>These tests exist because that failure was silent: the code compiled, the annotation
 * looked right, and every existing test passed.
 */
class ChatControllerSenderTest {

    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ChatController controller = new ChatController(messagingTemplate, chatMessageService);

    private static Gamer gamer(String id, String username) {
        Gamer g = new Gamer();
        g.setUserId(id);
        g.setGamerUsername(username);
        return g;
    }

    private static ChatMessageRequest request(String receiver, String message) {
        ChatMessageRequest r = new ChatMessageRequest();
        r.setReceiver(receiver);
        r.setMessage(message);
        return r;
    }

    private static Principal authenticated(Gamer g) {
        return new UsernamePasswordAuthenticationToken(g, null, g.getAuthorities());
    }

    @Test
    @DisplayName("the sender is taken from the authenticated session")
    void senderComesFromTheSession() {
        Gamer alice = gamer("u-alice", "alice");
        when(chatMessageService.save(anyString(), anyString(), anyString()))
                .thenReturn(new SentMessage(UUID.randomUUID(), "u-alice", "alice", "hi", Instant.now(), "bob@x.com"));

        controller.processMessage(authenticated(alice), request("u-bob", "hi"));

        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        verify(chatMessageService).save(sender.capture(), eq("u-bob"), eq("hi"));
        assertEquals("u-alice", sender.getValue());
    }

    @Test
    @DisplayName("an unauthenticated frame is refused rather than defaulted to somebody")
    void unauthenticatedIsRefused() {
        assertThrows(BusinessException.class, () -> controller.processMessage(null, request("u-bob", "hi")));
        verifyNoInteractions(chatMessageService);
    }

    @Test
    @DisplayName("a principal that is not a Gamer is refused, not coerced")
    void foreignPrincipalIsRefused() {
        Principal odd = () -> "not-a-gamer";

        assertThrows(BusinessException.class, () -> controller.processMessage(odd, request("u-bob", "hi")));
        verifyNoInteractions(chatMessageService);
    }

    @Test
    @DisplayName("the payload cannot influence who the message is from")
    void payloadCannotSetTheSender() {
        Gamer alice = gamer("u-alice", "alice");
        when(chatMessageService.save(anyString(), anyString(), anyString()))
                .thenReturn(new SentMessage(UUID.randomUUID(), "u-alice", "alice", "x", Instant.now(), "bob@x.com"));

        // ChatMessageRequest carries only a receiver and a message. There is deliberately
        // no sender field for a client to populate, and the signature takes the sender from
        // Principal, so there is nowhere for a forged identity to enter.
        controller.processMessage(authenticated(alice), request("u-bob", "x"));

        verify(chatMessageService).save(eq("u-alice"), eq("u-bob"), eq("x"));
    }
}
