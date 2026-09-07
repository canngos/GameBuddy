package com.gamebuddy.match.config;

import com.gamebuddy.match.domain.service.chat.LocalPresenceRegistry;
import com.gamebuddy.match.domain.service.chat.PresenceRegistry;
import com.gamebuddy.match.domain.service.chat.RedisPresenceRegistry;
import java.time.Clock;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Chooses where presence lives: this instance's memory, or a view shared over Redis.
 *
 * <p>Socket <em>delivery</em> wiring used to live here too; it moved to
 * {@code com.gamebuddy.shared.messaging.MessagingConfig} when lobbies became a second
 * feature pushing over the socket. Presence stays: only chat shows online dots, so the
 * registry is still this module's business.
 *
 * <p>Keyed on whether a Redis host is configured, for the same reason as the delivery
 * config: there is one fact, "is there a Redis", and one place it is read.
 */
@Slf4j
@Configuration
public class ChatMessagingConfig {

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
}
