package com.gamebuddy.match.config;

import com.gamebuddy.match.domain.service.chat.LocalPresenceRegistry;
import com.gamebuddy.match.domain.service.chat.LocalUserMessaging;
import com.gamebuddy.match.domain.service.chat.PresenceRegistry;
import com.gamebuddy.match.domain.service.chat.RedisPresenceRegistry;
import com.gamebuddy.match.domain.service.chat.RedisUserMessaging;
import com.gamebuddy.match.domain.service.chat.UserMessaging;
import java.time.Clock;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Chooses how a push reaches a gamer: this instance's sockets, or every instance's.
 *
 * <p><b>Keyed on whether a Redis host is configured, not on a flag of our own.</b> A
 * separate {@code gamebuddy.chat.redis.enabled} would be a second switch that can disagree
 * with the first — Redis configured and fan-out off is a cluster that silently drops half
 * its messages, and the opposite is an application that will not start. There is one fact
 * here, "is there a Redis", and one place it is read.
 *
 * <p>With no host set the application behaves exactly as it did before Redis existed:
 * local delivery, no container to run, nothing new that can be down. That is the right
 * default for a single-box deployment and for development.
 */
@Slf4j
@Configuration
public class ChatMessagingConfig {

    /**
     * The fan-out, active as soon as {@code spring.data.redis.host} is set.
     *
     * <p>{@code matchIfMissing = false}: absent configuration means no Redis, so the local
     * implementation below is used.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public UserMessaging redisUserMessaging(
            StringRedisTemplate redis, SimpMessagingTemplate broker, ObjectMapper json) {
        log.info(
                "Chat delivery is fanned out over Redis topic {}",
                RedisUserMessaging.topic().getTopic());
        return new RedisUserMessaging(redis, broker, json);
    }

    /**
     * Subscribes this instance to the channel.
     *
     * <p>Only created alongside the Redis implementation, because a listener container with
     * nothing to hand messages to would open a connection to keep a promise nobody made.
     *
     * <p>No serialiser is configured on the container. It is shared machinery, and giving
     * it an opinion about encoding would make every future subscriber inherit it; the
     * listener decodes its own bytes instead.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public RedisMessageListenerContainer socketDeliveryListener(
            RedisConnectionFactory connectionFactory, UserMessaging userMessaging) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // The implementation is its own listener, so the channel it publishes to and the
        // channel it listens on cannot drift apart.
        if (userMessaging instanceof RedisUserMessaging redis) {
            container.addMessageListener(redis, RedisUserMessaging.topic());
        }
        return container;
    }

    /**
     * Presence shared across instances.
     *
     * <p>The instance id is generated per process rather than configured. It only has to be
     * unique among the instances running at any moment, and a configured one is a value
     * somebody eventually copies into a second deployment — at which point two instances
     * overwrite each other's presence field and half the sockets vanish from the shared
     * view. A random id cannot be got wrong.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public PresenceRegistry redisPresenceRegistry(StringRedisTemplate redis, Clock clock) {
        String instanceId = UUID.randomUUID().toString();
        log.info("Presence is shared through Redis; this instance is {}", instanceId);
        return new RedisPresenceRegistry(redis, instanceId, clock);
    }

    @Bean
    @ConditionalOnMissingBean(PresenceRegistry.class)
    public PresenceRegistry localPresenceRegistry(Clock clock) {
        return new LocalPresenceRegistry(clock);
    }

    /**
     * The default: deliver to sockets this process holds.
     *
     * <p>{@code @ConditionalOnMissingBean} rather than the inverse of the property above,
     * so the two cannot both be absent — whatever happens to the condition, exactly one
     * {@link UserMessaging} exists and the application starts.
     */
    @Bean
    @ConditionalOnMissingBean(UserMessaging.class)
    public UserMessaging localUserMessaging(SimpMessagingTemplate broker) {
        log.info("Chat delivery is local to this instance; set spring.data.redis.host to fan out");
        return new LocalUserMessaging(broker);
    }
}
