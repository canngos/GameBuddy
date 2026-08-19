package com.gamebuddy.match.config;

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
 * Throttles match decisions.
 *
 * <p>Solves a different problem from the daily allowance, which is why it exists alongside
 * it. The allowance is a monetisation lever measured in days; this is an abuse control
 * measured in a minute. A daily cap does not stop a script — the script simply runs to the
 * cap in two seconds and repeats tomorrow — and it is the *rate*, not the volume, that
 * distinguishes automation from a person looking at profiles.
 *
 * <p>Tunable at {@code gamebuddy.rate-limit.match.*}; see {@code application.yml} for the
 * environment variables. The default below is what ships.
 *
 * <p>Scheduling is enabled by {@code AsyncConfig}.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.rate-limit.match")
@Setter
@Getter
public class MatchRateLimitConfig {

    /**
     * Two decisions a second, sustained for a whole minute.
     *
     * <p>This was thirty a minute, on the reasoning that two seconds per profile is slower
     * than anyone reads one. That reasoning was wrong, and a production tester swiping at
     * an ordinary pace proved it: nobody reads every card. Passing on people who are
     * obviously not a match is a flick, several a second, and a deck is *designed* to be
     * gone through that way — so the budget was being spent in about fifteen seconds of
     * perfectly normal use.
     *
     * <p>The mistake was measuring against reading speed instead of against swiping speed.
     * A hundred and twenty a minute is above what a thumb can sustain even flicking flat
     * out, so the limiter now sits where it was always meant to: past every human, in
     * front of every script.
     *
     * <p>Loosening it costs little, because this is not the control that bounds how much
     * of the population one account can see. The daily allowance does that, and the deck
     * itself only ever offers candidates the recommender has already returned.
     */
    private Budget decision = Budget.of(120, Duration.ofMinutes(1));

    @Bean
    public RateLimiter decisionRateLimiter() {
        return decision.limiter();
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
