package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.ratelimit.RateLimiter;

/**
 * The three throttles guarding the unauthenticated auth endpoints.
 *
 * <p>Grouped into one holder rather than three same-typed beans so neither Spring nor
 * Mockito has to disambiguate {@code RateLimiter} by parameter name.
 *
 * @param verify guesses against a verification code, keyed by email
 * @param sendCode code emails requested, keyed by email
 * @param login failed sign-ins, keyed by username or email
 */
public record AuthRateLimiters(RateLimiter verify, RateLimiter sendCode, RateLimiter login) {

    /** Drops expired windows so the in-memory maps cannot grow without bound. */
    public void evictExpired() {
        verify.evictExpired();
        sendCode.evictExpired();
        login.evictExpired();
    }
}
