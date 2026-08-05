package com.gamebuddy.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal fixed-window rate limiter used to gate the unauthenticated auth endpoints.
 *
 * <p>The verification-code endpoints previously had no throttling at all, which is
 * what made a 6-digit code brute-forceable end to end.
 *
 * <p><strong>Scope:</strong> counters live in this JVM only. With more than one
 * replica the effective limit is {@code permits × replicas}, which is still a hard
 * ceiling on a brute-force attempt but is not exact. Swap the backing map for Redis
 * if you need a cluster-wide guarantee — the interface does not change.
 *
 * <p><strong>Bounded:</strong> the key is caller-supplied (an e-mail address), so an
 * unbounded map would itself be an attack: post a million distinct addresses to
 * {@code /auth/sendCode} and the pod runs out of heap long before the scheduled
 * eviction runs. {@link #DEFAULT_MAX_KEYS} caps the tracked set.
 *
 * <p><strong>Time:</strong> read through an injectable {@link Clock} so window
 * expiry can be tested by advancing a fake clock rather than by sleeping.
 */
public class RateLimiter {

    /** Roughly 20 MB of entries at the sizes these keys run to. */
    public static final int DEFAULT_MAX_KEYS = 100_000;

    private record Window(Instant resetAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int permits;
    private final Duration window;
    private final int maxKeys;
    private final Clock clock;

    public RateLimiter(int permits, Duration window) {
        this(permits, window, DEFAULT_MAX_KEYS, Clock.systemUTC());
    }

    public RateLimiter(int permits, Duration window, int maxKeys) {
        this(permits, window, maxKeys, Clock.systemUTC());
    }

    public RateLimiter(int permits, Duration window, int maxKeys, Clock clock) {
        this.permits = permits;
        this.window = window;
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /**
     * Records an attempt against {@code key}.
     *
     * @return {@code true} if the caller is within budget, {@code false} if throttled
     */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() >= maxKeys && !windows.containsKey(key)) {
            makeRoom(now);
        }
        Window current = windows.compute(
                key,
                (k, existing) -> existing == null || existing.resetAt().isBefore(now)
                        ? new Window(now.plus(window), new AtomicInteger())
                        : existing);
        return current.count().incrementAndGet() <= permits;
    }

    /** Clears the counter for {@code key}, e.g. after a successful login. */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Drops expired windows. Call periodically so stale keys do not accumulate. */
    public void evictExpired() {
        evictExpired(clock.instant());
    }

    private void evictExpired(Instant now) {
        windows.entrySet().removeIf(e -> e.getValue().resetAt().isBefore(now));
    }

    /** Number of keys currently tracked. Exposed for monitoring and tests. */
    public int size() {
        return windows.size();
    }

    /**
     * Enforces the cap before admitting a new key: expired windows go first, and if
     * that is not enough the windows closest to expiry are dropped. Evicting rather
     * than refusing keeps a flood of junk keys from locking out real users, at the
     * cost of some attackers getting a fresh budget sooner than they should.
     */
    private void makeRoom(Instant now) {
        evictExpired(now);
        if (windows.size() < maxKeys) {
            return;
        }
        windows.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().resetAt()))
                .limit(Math.max(1, windows.size() - maxKeys + 1))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(windows::remove);
    }
}
