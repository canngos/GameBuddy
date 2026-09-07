package com.gamebuddy.common.ratelimit;

import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;

/**
 * How many actions a limiter allows, and over how long.
 *
 * <p>A settable pair rather than a record, because these are bound from configuration by
 * {@code @ConfigurationProperties} on the {@code @Configuration} classes that own them,
 * and that path needs setters. The same shape as {@code StorageConfig}'s nested types.
 *
 * <p><strong>Every budget's shipping default lives in the Java field that declares it</strong>,
 * not in {@code application.yml}. The YAML repeats the number so the knob is discoverable
 * and documented next to its neighbours, but a deployment with no rate-limit configuration
 * at all still gets exactly what was tested. {@code RateLimitBudgetsTest} asserts the two
 * agree, so the copy in YAML cannot drift away from the one that ships.
 *
 * <h2>Why the setters take strings</h2>
 *
 * <p>Because an unset override has to be survivable, and through Docker Compose it does
 * not arrive as "absent" — it arrives as empty. {@code MATCH_DECISION_PERMITS: ${MATCH_DECISION_PERMITS}}
 * with nothing in {@code .env} sets the variable to the empty string inside the container,
 * and binding an empty string to an {@code int} throws: the application would refuse to
 * start because somebody did not tune a rate limit. Taking the raw text and ignoring it
 * when blank makes "not configured" mean what it says.
 *
 * <p>This is the same trap, and the same answer, that {@code StorageConfig} records for
 * {@code R2_ACCESS_KEY_ID} — an empty environment variable is what an unconfigured
 * deployment looks like, and treating it as a value is how that turns into an outage.
 */
public class Budget {

    private int permits;
    private Duration window;

    public Budget() {}

    private Budget(int permits, Duration window) {
        this.permits = permits;
        this.window = window;
    }

    /** Declares a default in the field that holds it, e.g. {@code Budget.of(120, ofMinutes(1))}. */
    public static Budget of(int permits, Duration window) {
        return new Budget(permits, window);
    }

    /** Blank leaves the declared default in place; see the class comment. */
    public void setPermits(String permits) {
        if (permits != null && !permits.isBlank()) {
            this.permits = Integer.parseInt(permits.trim());
        }
    }

    /**
     * Blank leaves the declared default in place. Accepts Boot's duration shorthand, so
     * {@code 30s}, {@code 15m} and {@code 1d} all work as well as {@code PT15M}.
     */
    public void setWindow(String window) {
        if (window != null && !window.isBlank()) {
            this.window = DurationStyle.detectAndParse(window.trim());
        }
    }

    public int permits() {
        return permits;
    }

    public Duration window() {
        return window;
    }

    /**
     * Builds the limiter this budget describes.
     *
     * <p>Validated here rather than at the field, because a misconfigured budget must stop
     * the application from starting. The alternative is a limiter that refuses everybody
     * (zero permits) or never expires a window (zero duration), and both look like the
     * feature being broken rather than like a typo in an environment variable.
     */
    public RateLimiter limiter() {
        if (permits < 1) {
            throw new IllegalStateException("A rate limit needs at least one permit, got " + permits);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("A rate limit needs a positive window, got " + window);
        }
        return new RateLimiter(permits, window);
    }

    @Override
    public String toString() {
        return permits + " per " + window;
    }
}
