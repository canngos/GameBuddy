package com.gamebuddy.common.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** Lets a test jump forward in time instead of sleeping through a real window. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void testTryAcquire_whenWithinBudget_ReturnsTrue() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void testTryAcquire_whenBudgetExhausted_ReturnsFalse() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1));

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        assertFalse(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    @DisplayName("budgets are per key, so one attacker cannot lock everyone else out")
    void testTryAcquire_whenDifferentKeys_BudgetsAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("attacker"));
        assertFalse(limiter.tryAcquire("attacker"));
        assertTrue(limiter.tryAcquire("victim"));
    }

    @Test
    void testTryAcquire_whenWindowElapsed_BudgetRefills() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(15), 100, clock);

        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));

        clock.advance(Duration.ofMinutes(16));

        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void testReset_whenCalled_ClearsTheCounter() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));
        limiter.tryAcquire("a");
        assertFalse(limiter.tryAcquire("a"));

        limiter.reset("a");

        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void testEvictExpired_whenWindowsHaveElapsed_DropsThem() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(15), 100, clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        assertEquals(2, limiter.size());

        clock.advance(Duration.ofMinutes(16));
        limiter.evictExpired();

        assertEquals(0, limiter.size());
    }

    @Test
    @DisplayName("a flood of unique keys cannot grow the map without bound")
    void testTryAcquire_whenKeysExceedTheCap_MapStaysBounded() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(10), 100);

        for (int i = 0; i < 10_000; i++) {
            limiter.tryAcquire("junk-" + i);
        }

        assertTrue(limiter.size() <= 100, "expected the cap to hold, saw " + limiter.size());
    }

    @Test
    @DisplayName("concurrent callers on one key cannot exceed the budget")
    void testTryAcquire_whenCalledConcurrently_GrantsExactlyThePermits() throws Exception {
        int threads = 32;
        int permits = 10;
        RateLimiter limiter = new RateLimiter(permits, Duration.ofMinutes(1));
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    if (limiter.tryAcquire("shared")) {
                        granted.incrementAndGet();
                    }
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));

        assertEquals(permits, granted.get());
    }
}
