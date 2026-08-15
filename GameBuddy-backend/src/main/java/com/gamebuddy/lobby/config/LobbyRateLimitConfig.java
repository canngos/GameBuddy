package com.gamebuddy.lobby.config;

import com.gamebuddy.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Abuse controls on the lobby surfaces, same shape as {@code MatchRateLimitConfig}.
 *
 * <p>Each limiter guards a different pest: creates against Gold accounts spamming the
 * browse feed (the one-active-lobby rule already binds, so this is belt and braces for
 * create-cancel loops), joins against carpet-requesting every lobby in the feed, and
 * messages against flooding a chat five people are trapped in with.
 *
 * <p>Scheduling is enabled by {@code AsyncConfig}.
 */
@Configuration
public class LobbyRateLimitConfig {

    /** Five a day. A person plans a handful of sessions; a loop making lobbies is not a person. */
    @Bean
    public RateLimiter lobbyCreateRateLimiter() {
        return new RateLimiter(5, Duration.ofDays(1));
    }

    /** Ten an hour — a real evening of browsing, but not one of asking everybody. */
    @Bean
    public RateLimiter lobbyJoinRateLimiter() {
        return new RateLimiter(10, Duration.ofHours(1));
    }

    /** Twenty a minute, comfortably above the fastest genuine typing in a planning chat. */
    @Bean
    public RateLimiter lobbyMessageRateLimiter() {
        return new RateLimiter(20, Duration.ofMinutes(1));
    }

    @Component
    @RequiredArgsConstructor
    static class LobbyRateLimiterHousekeeping {

        private final RateLimiter lobbyCreateRateLimiter;
        private final RateLimiter lobbyJoinRateLimiter;
        private final RateLimiter lobbyMessageRateLimiter;

        @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
        void evict() {
            lobbyCreateRateLimiter.evictExpired();
            lobbyJoinRateLimiter.evictExpired();
            lobbyMessageRateLimiter.evictExpired();
        }
    }
}
