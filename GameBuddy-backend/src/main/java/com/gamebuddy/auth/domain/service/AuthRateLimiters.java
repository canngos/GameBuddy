package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.ratelimit.RateLimiter;

/**
 * The throttles guarding the auth endpoints.
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
 * @param link Discord link attempts, keyed by user id. The odd one out: it guards an
 *     authenticated endpoint, so it is not protecting a credential — it stops one account
 *     minting link tickets in a loop, and it is the only budget here keyed by account rather
 *     than by the address somebody typed.
 * @param social social sign-in attempts, keyed by a hash of the credential presented. Not by
 *     account and not by address: at this point in the flow there is no session, and the
 *     address is inside a token that has not been verified yet. Hashing what was presented
 *     throttles a retry loop without keeping anything worth having.
 */
public record AuthRateLimiters(
        RateLimiter verify,
        RateLimiter sendCode,
        RateLimiter login,
        RateLimiter resetPassword,
        RateLimiter link,
        RateLimiter social) {

    /** Drops expired windows so the in-memory maps cannot grow without bound. */
    public void evictExpired() {
        verify.evictExpired();
        sendCode.evictExpired();
        login.evictExpired();
        resetPassword.evictExpired();
        link.evictExpired();
        social.evictExpired();
    }
}
