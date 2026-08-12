package com.gamebuddy.match.domain.service.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The fan-out: publish on send, deliver on receive, and never both.
 *
 * <p>The two implementations are interchangeable to every caller, so what is worth testing
 * is only what differs — the wire format, the loopback, and what happens when Redis is not
 * there.
 */
@DisplayName("RedisUserMessaging")
class RedisUserMessagingTest {

    /** Stands in for a payload record; the shape is what ends up on the wire. */
    private record Notification(String id, String body) {}

    private StringRedisTemplate redis;
    private SimpMessagingTemplate broker;
    private ObjectMapper json;
    private RedisUserMessaging messaging;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        broker = mock(SimpMessagingTemplate.class);
        json = new ObjectMapper();
        messaging = new RedisUserMessaging(redis, broker, json);
    }

    private String published() {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(redis).convertAndSend(anyString(), body.capture());
        return body.getValue().toString();
    }

    @Nested
    @DisplayName("sending")
    class Sending {

        @Test
        @DisplayName("publishes rather than delivering locally")
        void publishesOnly() {
            messaging.sendToUser("someone@example.com", "/queue/messages", new Notification("1", "hi"));

            verify(redis).convertAndSend(anyString(), any());
            // The publisher gets its own message back from Redis like everyone else, so
            // delivering here as well would show it to the recipient twice.
            verifyNoInteractions(broker);
        }

        @Test
        @DisplayName("carries the recipient, the destination and the payload")
        void carriesEverythingNeeded() {
            messaging.sendToUser("someone@example.com", "/queue/typing", new Notification("1", "hi"));

            SocketDelivery delivery = json.readValue(published(), SocketDelivery.class);
            assertEquals("someone@example.com", delivery.principalName());
            assertEquals("/queue/typing", delivery.destination());
            assertEquals("{\"id\":\"1\",\"body\":\"hi\"}", delivery.payloadJson());
        }
    }

    @Nested
    @DisplayName("receiving")
    class Receiving {

        @Test
        @DisplayName("a round trip reaches the broker with the payload intact")
        void roundTripPreservesThePayload() {
            messaging.sendToUser("someone@example.com", "/queue/messages", new Notification("42", "hello"));
            messaging.onMessage(new DefaultMessage(new byte[0], published().getBytes(StandardCharsets.UTF_8)), null);

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(broker).convertAndSendToUser(eq("someone@example.com"), eq("/queue/messages"), payload.capture());

            // Maps and lists, not the JSON string. Handing the broker a string would have
            // Spring serialise it a second time and the client would receive an escaped
            // string where it expects an object — the same bytes, one level too deep.
            assertEquals(Map.of("id", "42", "body", "hello"), payload.getValue());
        }

        @Test
        @DisplayName("rubbish on the channel is swallowed, not thrown")
        void malformedMessageIsIgnored() {
            // This runs on Redis's listener thread. An exception escaping it would take
            // down the subscription the whole cluster's chat depends on.
            assertDoesNotThrow(() -> messaging.onMessage(
                    new DefaultMessage(new byte[0], "not json".getBytes(StandardCharsets.UTF_8)), null));
            verifyNoInteractions(broker);
        }
    }

    @Nested
    @DisplayName("when Redis is down")
    class RedisDown {

        @Test
        @DisplayName("it delivers locally instead of losing the message")
        void fallsBackToLocalDelivery() {
            when(redis.convertAndSend(anyString(), any())).thenThrow(new RuntimeException("connection refused"));

            Notification notification = new Notification("1", "hi");
            messaging.sendToUser("someone@example.com", "/queue/messages", notification);

            // With one instance this is complete delivery, so a Redis outage costs nothing.
            // Chat stopping because a cache is down would be worse than the problem Redis
            // was added to solve.
            verify(broker).convertAndSendToUser("someone@example.com", "/queue/messages", notification);
        }
    }
}
