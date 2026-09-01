package com.gamebuddy.billing.config;

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
 * Abuse control on redeeming a promotion code, same shape as {@code LobbyRateLimitConfig}.
 *
 * <p>The only limiter in this module, and the only one in the application whose job is
 * genuinely to stop guessing rather than to stop flooding. Everything else here is a
 * webhook the stores authenticate, or a read.
 *
 * <p>Tunable at {@code gamebuddy.rate-limit.billing.*}; see {@code application.yml} for the
 * environment variables. The defaults below are what ship.
 *
 * <p>Scheduling is enabled by {@code AsyncConfig}.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.rate-limit.billing")
@Setter
@Getter
public class BillingRateLimitConfig {

    /**
     * Twenty an hour, per account.
     *
     * <p>Set the way the rest of this block is: above what a person does, not above what a
     * person ought to need. Somebody with a code from a newsletter mistypes it once or
     * twice and reads it back off a screen; twenty is far past that and far below anything
     * that would make guessing worth attempting. Eight characters over a thirty-one symbol
     * alphabet is about 1.5e12 possibilities, so twenty tries an hour is not a defence that
     * has to be tight to work — it only has to make the attempt pointless, and it needs an
     * account to spend the tries from.
     *
     * <p>Keyed by the redeeming account, deliberately, not by the code: keying by code
     * would let one attacker lock a real campaign code out for everybody holding it.
     */
    private Budget promoRedeem = Budget.of(20, Duration.ofHours(1));

    @Bean
    public RateLimiter promoRedeemRateLimiter() {
        return promoRedeem.limiter();
    }

    @Component
    @RequiredArgsConstructor
    static class BillingRateLimiterHousekeeping {

        private final RateLimiter promoRedeemRateLimiter;

        @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
        void evict() {
            promoRedeemRateLimiter.evictExpired();
        }
    }
}
