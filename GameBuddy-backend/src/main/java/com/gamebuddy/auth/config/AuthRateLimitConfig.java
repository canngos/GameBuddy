package com.gamebuddy.auth.config;

import com.gamebuddy.auth.domain.service.AuthRateLimiters;
import com.gamebuddy.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Throttles the unauthenticated auth endpoints.
 *
 * <p>Without this, {@code /auth/verify} could be walked through the entire 900k
 * six-digit code space, and {@code /auth/sendCode} could be used to mail-bomb any
 * registered address.
 */
@Configuration
@EnableScheduling
public class AuthRateLimitConfig {

    @Bean
    public AuthRateLimiters authRateLimiters() {
        return new AuthRateLimiters(
                new RateLimiter(10, Duration.ofMinutes(15)),
                new RateLimiter(3, Duration.ofMinutes(15)),
                new RateLimiter(10, Duration.ofMinutes(15)),
                new RateLimiter(10, Duration.ofMinutes(15)));
    }

    @Component
    @RequiredArgsConstructor
    static class AuthRateLimiterHousekeeping {

        private final AuthRateLimiters limiters;

        // Runs well inside the 15-minute window so expired keys do not linger for a
        // second window's worth of time before being reclaimed.
        @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
        void evict() {
            limiters.evictExpired();
        }
    }
}
