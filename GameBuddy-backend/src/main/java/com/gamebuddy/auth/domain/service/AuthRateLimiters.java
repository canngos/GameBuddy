package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.ratelimit.RateLimiter;

/**
 * The four throttles guarding the unauthenticated auth endpoints.
 *
 * <p>Grouped into one holder rather than three same-typed beans so neither Spring nor
 * Mockito has to disambiguate {@code RateLimiter} by parameter name.
 *
 * @param verify guesses against a verification code, keyed by email
 * @param sendCode code emails requested, keyed by email
 * @param login failed sign-ins, keyed by username or email
 * @param resetPassword the two password-reset steps, keyed by email. Its own throttle rather
 *     than sharing {@code verify}, so somebody resetting a password cannot exhaust the budget
 *     that a half-finished signup needs, and vice versa.
 */
public record AuthRateLimiters(
        RateLimiter verify, RateLimiter sendCode, RateLimiter login, RateLimiter resetPassword) {

    /** Drops expired windows so the in-memory maps cannot grow without bound. */
    public void evictExpired() {
        verify.evictExpired();
        sendCode.evictExpired();
        login.evictExpired();
        resetPassword.evictExpired();
    }
}
