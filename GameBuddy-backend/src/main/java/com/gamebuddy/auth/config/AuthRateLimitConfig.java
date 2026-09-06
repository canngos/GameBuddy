package com.gamebuddy.auth.config;

import com.gamebuddy.auth.domain.service.AuthRateLimiters;
import com.gamebuddy.common.ratelimit.Budget;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 *
 * <p>All four are fixed windows keyed by the caller-supplied identifier, not by IP, so
 * one person cannot lock a whole network out and a botnet gains nothing by spreading a
 * guess across addresses. The window starts at the first attempt and is <em>not</em>
 * extended by a rejected one — being throttled never pushes the unlock further away.
 *
 * <p><strong>Every budget here was raised after production testing.</strong> The original
 * numbers were picked against an imagined user who does each thing once, deliberately,
 * and got in the way of a real one who retries. The principle they should have been
 * picked on, and now are: a rate limit belongs above the fastest a person could plausibly
 * go, not above the fastest they ought to need to. Anything tighter spends its time
 * refusing customers, because an attacker with a script is nowhere near these numbers
 * either way — they are orders of magnitude past them.
 *
 * <p>Tunable at {@code gamebuddy.rate-limit.auth.*}; see {@code application.yml} for the
 * environment variables. The defaults below are what ship.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.rate-limit.auth")
@EnableScheduling
@Setter
@Getter
public class AuthRateLimitConfig {

    /**
     * Guesses at a mailed six-digit code, per address.
     *
     * <p>Raised from ten, which is safe to do because this limiter was never the thing
     * holding the code space shut. {@code MAX_CODE_ATTEMPTS} in {@code DefaultAuthService}
     * is: five wrong guesses invalidate the code itself, so brute force cannot proceed by
     * guessing at all — it has to keep asking for fresh codes, and that is bounded by
     * {@link #sendCode} below. What this budget actually does is stop somebody cycling
     * code-then-guess in a loop, and twenty a quarter-hour does that just as well as ten
     * while leaving room for a person who fumbles a code, requests another and fumbles
     * that one too.
     */
    private Budget verify = Budget.of(20, Duration.ofMinutes(15));

    /**
     * Code emails per address, and the tightest budget here by a wide margin — because
     * each permit spends somebody else's inbox, and the victim of a mail flood is not the
     * person making the requests.
     *
     * <p>Raised from three only as far as six. Three is genuinely too few: a code that is
     * slow to arrive gets tapped twice before it lands, and a third tap is somebody
     * checking their spam folder, not attacking anyone. Six covers that and is still
     * nowhere near enough volume to be worth aiming at an inbox.
     */
    private Budget sendCode = Budget.of(6, Duration.ofMinutes(15));

    /**
     * Failed sign-ins per account, and the one budget here that an ordinary user meets on
     * an ordinary day.
     *
     * <p>It was ten per fifteen minutes, and a production tester was locked out of their
     * own account while doing nothing more unusual than trying to remember a password.
     * Both halves of that were wrong. Ten is not many attempts for somebody working
     * through the two or three passwords they might have used, and fifteen minutes is a
     * punishment out of all proportion to mistyping.
     *
     * <p>Loosening this is affordable in a way loosening {@link #verify} would not be,
     * even though the numbers look similar. A six-digit code has 900k possibilities and
     * falls to patience; a password does not, and this limiter was never what made it
     * safe — the hashing and the password rules are. What the budget has to stop is
     * somebody sitting on one account running a wordlist, and 180 guesses an hour is as
     * hopeless against that as 40 was. Credential stuffing is untouched either way: it
     * plays a handful of guesses against a great many accounts, so a per-account counter
     * never sees it.
     *
     * <p>A successful login clears the counter immediately, so somebody who finally
     * remembers their password walks away with a clean slate rather than one stumble from
     * a lockout for the rest of the window.
     */
    private Budget login = Budget.of(15, Duration.ofMinutes(5));

    /**
     * The two password-reset steps, per address, sharing one budget.
     *
     * <p>Sharing is why this needs to be generous: a single completed reset spends two
     * permits, so ten was really five resets, and a reset abandoned halfway — which is
     * what happens when the email is slow — spends one and finishes nothing. Twenty is
     * ten complete resets in a quarter of an hour, which no honest person reaches and no
     * attacker benefits from, since every attempt still has to present a mailed code that
     * {@code MAX_CODE_ATTEMPTS} invalidates after five wrong guesses.
     */
    private Budget resetPassword = Budget.of(20, Duration.ofMinutes(15));

    /**
     * Link attempts against Discord, per account.
     *
     * <p>The only budget here on an authenticated endpoint, so it is not standing between
     * anybody and a credential — the JWT already did that. What it stops is one account
     * minting link tickets in a loop, each of which is a database row and an outbound request
     * to somebody else's service.
     *
     * <p>Generous, because linking is genuinely fiddly the first time: a consent screen
     * abandoned, a wrong Discord account signed in, then the right one. Ten in a
     * quarter of an hour is well past that and nowhere near worth scripting.
     */
    private Budget link = Budget.of(10, Duration.ofMinutes(15));

    /**
     * Social sign-in attempts, keyed by a hash of the credential presented.
     *
     * <p>Unauthenticated, like the four above it, but guarding something different: a
     * Google ID token is not guessable, so this is not standing between anybody and a
     * credential. What it stops is a client stuck in a retry loop calling Google's key
     * endpoint and this database through us.
     *
     * <p>Generous for that reason, and because one honest sign-in can legitimately be two
     * calls: a brand-new account is refused the first time for the terms, and the app
     * retries the same token once the box is ticked. Thirty is fifteen of those.
     */
    private Budget social = Budget.of(30, Duration.ofMinutes(15));

    @Bean
    public AuthRateLimiters authRateLimiters() {
        return new AuthRateLimiters(
                verify.limiter(),
                sendCode.limiter(),
                login.limiter(),
                resetPassword.limiter(),
                link.limiter(),
                social.limiter());
    }

    @Component
    @RequiredArgsConstructor
    static class AuthRateLimiterHousekeeping {

        private final AuthRateLimiters limiters;

        // Runs well inside the shortest window so expired keys do not linger for a
        // second window's worth of time before being reclaimed.
        @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
        void evict() {
            limiters.evictExpired();
        }
    }
}
