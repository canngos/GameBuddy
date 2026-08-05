package com.gamebuddy.match.config;

import com.gamebuddy.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Throttles match decisions.
 *
 * <p>Solves a different problem from the daily allowance, which is why it exists alongside
 * it. The allowance is a monetisation lever measured in days; this is an abuse control
 * measured in a minute. A daily cap does not stop a script — the script simply runs to the
 * cap in two seconds and repeats tomorrow — and it is the *rate*, not the volume, that
 * distinguishes automation from a person looking at profiles.
 *
 * <p>Scheduling is enabled by {@code AsyncConfig}.
 */
@Configuration
public class MatchRateLimitConfig {

    /**
     * Thirty decisions a minute.
     *
     * <p>Two seconds per profile is already faster than anyone reads one, so a real user
     * never reaches this even while swiping quickly. Set low enough to make enumeration
     * tedious, high enough that a fast human never notices it.
     */
    @Bean
    public RateLimiter decisionRateLimiter() {
        return new RateLimiter(30, Duration.ofMinutes(1));
    }

    @Component
    @RequiredArgsConstructor
    static class MatchRateLimiterHousekeeping {

        private final RateLimiter decisionRateLimiter;

        // Well inside the one-minute window, so expired keys are reclaimed promptly
        // rather than accumulating for a second window's worth of time.
        @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
        void evict() {
            decisionRateLimiter.evictExpired();
        }
    }
}
