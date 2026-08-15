package com.gamebuddy.shared.messaging;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivery to a gamer's socket wherever in the cluster it is held.
 *
 * <p>Every push is published to one Redis channel and every instance is subscribed to it.
 * On receipt an instance calls its own broker, which delivers if it happens to hold that
 * gamer's socket and does nothing if it does not. Nobody has to know who holds what, and
 * no instance has to talk to another directly.
 *
 * <p><b>Publishing never delivers locally.</b> The publisher receives its own message back
 * from Redis like everyone else, so the delivery path is one path rather than two, and a
 * message to somebody on this very instance travels the same route as one to somebody on
 * another. Sending locally as well would deliver it twice.
 *
 * <p><b>If Redis is unreachable, it delivers locally instead.</b> That is the honest
 * degradation: with one instance — the deployment this actually runs on — local delivery is
 * complete, so a Redis outage costs nothing at all. With several it costs cross-instance
 * delivery for the duration, which is the same as being briefly offline, and chat messages
 * are already durable in the database. The alternative is chat stopping entirely because a
 * cache is down, which is worse than the problem Redis was added to solve.
 */
@Slf4j
public class RedisUserMessaging implements UserMessaging, MessageListener {

    /**
     * The one channel everything goes through.
     *
     * <p>Not a channel per gamer. Redis Pub/Sub delivers to every subscriber regardless, so
     * per-gamer channels would buy no reduction in traffic and would cost a subscribe and
     * an unsubscribe on every connect — thousands of subscription changes an hour to save
     * nothing.
     */
    private static final String CHANNEL = "gamebuddy:socket";

    /**
     * The topic to subscribe this instance to.
     *
     * <p>Exposed rather than the raw name so the channel stays this class's business: the
     * configuration registers the listener it is given on the topic it is given, and cannot
     * drift from what {@link #sendToUser} publishes to.
     */
    public static ChannelTopic topic() {
        return new ChannelTopic(CHANNEL);
    }

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate broker;

    /**
     * The application's own mapper, not one built here.
     *
     * <p>Jackson 3, because that is what Boot 4 configures and what the broker's converter
     * uses on the local path. A mapper of our own would be a second opinion about naming,
     * nulls and dates, so the JSON a client received would depend on whether the sender
     * happened to be on their instance — the one difference this whole class exists to
     * remove.
     */
    private final ObjectMapper json;

    public RedisUserMessaging(StringRedisTemplate redis, SimpMessagingTemplate broker, ObjectMapper json) {
        this.redis = redis;
        this.broker = broker;
        this.json = json;
    }

    @Override
    public void sendToUser(String principalName, String destination, Object payload) {
        String body;
        try {
            body = json.writeValueAsString(
                    new SocketDelivery(principalName, destination, json.writeValueAsString(payload)));
        } catch (Exception e) {
            // Nothing can be done with a payload that will not serialise, and it would fail
            // identically on the local path, so there is no fallback worth attempting.
            log.warn("Could not serialise a socket delivery to {}", destination, e);
            return;
        }

        try {
            redis.convertAndSend(CHANNEL, body);
        } catch (RuntimeException e) {
            log.warn("Redis publish failed; delivering to local sockets only", e);
            broker.convertAndSendToUser(principalName, destination, payload);
        }
    }

    /**
     * Hands one published delivery to this instance's broker.
     *
     * <p>Called for every message from every instance, including our own. Failures are
     * swallowed: this runs on Redis's listener thread, and an exception escaping it would
     * take down the subscription that the whole cluster's chat depends on, to report one
     * push that nobody was waiting for.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            SocketDelivery delivery = json.readValue(body, SocketDelivery.class);
            // Parsed back into maps and lists rather than forwarded as a string — see
            // SocketDelivery for what forwarding the string would do to the client.
            Object payload = json.readValue(delivery.payloadJson(), Object.class);
            broker.convertAndSendToUser(delivery.principalName(), delivery.destination(), payload);
        } catch (RuntimeException e) {
            log.warn("Could not deliver a socket message from Redis", e);
        }
    }
}
