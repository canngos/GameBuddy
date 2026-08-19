package com.gamebuddy.lobby.config;

import com.gamebuddy.common.ratelimit.Budget;
import com.gamebuddy.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 * <p>All three were tuned against an imagined careful user and were consequently too
 * tight for a real one — the same mistake, and the same correction, as the deck's
 * decision limiter. A rate limit exists to catch automation, and the honest way to place
 * one is above the fastest a person could go, not above the fastest a person *should*
 * need to.
 *
 * <p>Tunable at {@code gamebuddy.rate-limit.lobby.*}; see {@code application.yml} for the
 * environment variables. The defaults below are what ship.
 *
 * <p>Scheduling is enabled by {@code AsyncConfig}.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.rate-limit.lobby")
@Setter
@Getter
public class LobbyRateLimitConfig {

    /**
     * Twenty a day.
     *
     * <p>Was five, which is fine for someone planning their own evening and hopeless for
     * anyone still working out what they want: the count is spent by *creating*, and a
     * lobby created and then cancelled because the time was wrong costs the same as one
     * that fills up. The real ceiling here has never been this number anyway — one live
     * lobby per owner is enforced separately, and that is what stops the browse feed
     * filling with one person's lobbies. This is only here to stop a create-cancel loop.
     */
    private Budget create = Budget.of(20, Duration.ofDays(1));

    /**
     * Forty an hour.
     *
     * <p>Was ten, which a browsing session reaches without doing anything unusual: asking
     * to join is how you find out whether a team has room, most requests are never
     * answered, and the feed on a busy evening is longer than ten. Forty still refuses
     * anybody carpeting the whole feed on a loop.
     */
    private Budget join = Budget.of(40, Duration.ofHours(1));

    /**
     * Sixty a minute — one a second, sustained.
     *
     * <p>Was twenty, and a squad settling on a time talks in short bursts of very short
     * messages, which is exactly the shape that trips a per-minute counter. Nobody types
     * a message a second for a solid minute, so this stays clear of real chat while still
     * cutting off a flood.
     */
    private Budget message = Budget.of(60, Duration.ofMinutes(1));

    @Bean
    public RateLimiter lobbyCreateRateLimiter() {
        return create.limiter();
    }

    @Bean
    public RateLimiter lobbyJoinRateLimiter() {
        return join.limiter();
    }

    @Bean
    public RateLimiter lobbyMessageRateLimiter() {
        return message.limiter();
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
