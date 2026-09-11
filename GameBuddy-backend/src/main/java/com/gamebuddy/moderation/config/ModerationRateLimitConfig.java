package com.gamebuddy.moderation.config;

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
 * How often one account may report, same shape as {@code LobbyRateLimitConfig}.
 *
 * <p>One budget, and a generous one. The thing it exists to stop is a script — or one
 * furious person — reporting every profile on the deck to bury a moderator, not somebody
 * who had a genuinely bad evening and reported three people in it. Reports are already
 * one per reporter per item, so this is the only lever a single account has left for
 * flooding the queue, and ten a day closes it without ever being felt by an honest user.
 *
 * <p>Deliberately not zero for anybody, ever. The child-safety policy promises the report
 * control exists on every profile; a reporter whose reports keep being dismissed is
 * weighed down by {@code ReportPolicy}, not silenced.
 *
 * <p>Tunable at {@code gamebuddy.rate-limit.moderation.*}; see {@code application.yml}.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.rate-limit.moderation")
@Setter
@Getter
public class ModerationRateLimitConfig {

    private Budget report = Budget.of(10, Duration.ofDays(1));

    @Bean
    public RateLimiter reportRateLimiter() {
        return report.limiter();
    }

    @Component
    @RequiredArgsConstructor
    static class ModerationRateLimiterHousekeeping {

        private final RateLimiter reportRateLimiter;

        @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
        void evict() {
            reportRateLimiter.evictExpired();
        }
    }
}
