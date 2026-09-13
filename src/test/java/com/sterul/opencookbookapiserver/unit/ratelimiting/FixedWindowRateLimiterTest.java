package com.sterul.opencookbookapiserver.unit.ratelimiting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.ratelimiting.FixedWindowRateLimiter;
import com.sterul.opencookbookapiserver.unit.MovableClock;

/**
 * The rate limiter without a servlet or a database anywhere near it.
 *
 * Time is injected rather than waited for, which is the only way the window rolling over is
 * testable at all - the production window is an hour long.
 */
class FixedWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);

    /** More keys than any test here creates, so the cap is out of the way unless it is the subject. */
    private static final int MANY_KEYS = 1000;

    private final MovableClock clock = new MovableClock(Instant.parse("2026-09-01T10:00:00Z"));

    @Test
    void requestsWithinTheBudgetAreAllowed() {
        var cut = new FixedWindowRateLimiter(3, WINDOW, MANY_KEYS, clock);

        assertTrue(cut.tryAcquire("client").allowed());
        assertTrue(cut.tryAcquire("client").allowed());
        assertTrue(cut.tryAcquire("client").allowed());
    }

    @Test
    void theRequestAfterTheBudgetIsRefused() {
        var cut = new FixedWindowRateLimiter(2, WINDOW, MANY_KEYS, clock);
        cut.tryAcquire("client");
        cut.tryAcquire("client");

        assertFalse(cut.tryAcquire("client").allowed());
    }

    @Test
    void aRefusalSaysHowLongToWait() {
        var cut = new FixedWindowRateLimiter(1, WINDOW, MANY_KEYS, clock);
        cut.tryAcquire("client");
        clock.advanceBy(Duration.ofMinutes(20));

        assertEquals(Duration.ofMinutes(40), cut.tryAcquire("client").retryAfter());
    }

    @Test
    void keysHaveBudgetsOfTheirOwn() {
        var cut = new FixedWindowRateLimiter(1, WINDOW, MANY_KEYS, clock);
        cut.tryAcquire("one client");

        assertTrue(cut.tryAcquire("another client").allowed());
    }

    @Test
    void theBudgetRefillsWhenTheWindowEnds() {
        var cut = new FixedWindowRateLimiter(1, WINDOW, MANY_KEYS, clock);
        cut.tryAcquire("client");
        assertFalse(cut.tryAcquire("client").allowed());

        clock.advanceBy(WINDOW);

        assertTrue(cut.tryAcquire("client").allowed());
    }

    @Test
    void carryingOnCallingDoesNotBuyAnEarlierWayBackIn() {
        var cut = new FixedWindowRateLimiter(1, WINDOW, MANY_KEYS, clock);
        cut.tryAcquire("client");

        // Twenty minutes of hammering, then a pause: the window is what expires, not the quiet
        for (var minute = 0; minute < 20; minute++) {
            clock.advanceBy(Duration.ofMinutes(1));
            assertFalse(cut.tryAcquire("client").allowed());
        }
        clock.advanceBy(Duration.ofMinutes(39));
        assertFalse(cut.tryAcquire("client").allowed());

        clock.advanceBy(Duration.ofMinutes(1));
        assertTrue(cut.tryAcquire("client").allowed());
    }

    @Test
    void endedWindowsAreEvictedAndLiveOnesAreKept() {
        var cut = new FixedWindowRateLimiter(1, WINDOW, MANY_KEYS, clock);
        cut.tryAcquire("early client");
        clock.advanceBy(Duration.ofMinutes(59));
        cut.tryAcquire("late client");

        clock.advanceBy(Duration.ofMinutes(1));

        assertEquals(1, cut.evictEndedWindows());
        assertFalse(cut.tryAcquire("late client").allowed(), "The live window must survive eviction");
    }

    @Test
    void aFullTableKeepsCountingAndForgetsTheCallerHeardFromLongestAgo() {
        var cut = new FixedWindowRateLimiter(1, WINDOW, 2, clock);
        cut.tryAcquire("first caller");
        cut.tryAcquire("second caller");

        assertFalse(cut.tryAcquire("second caller").allowed());

        // Makes room rather than giving up: a caller that does not fit must never mean nobody
        // is counted, or presenting enough distinct callers would buy unlimited attempts.
        assertTrue(cut.tryAcquire("third caller").allowed());
        assertFalse(cut.tryAcquire("third caller").allowed());

        // The price of a bounded table: whoever was displaced starts over.
        assertTrue(cut.tryAcquire("first caller").allowed());
    }

    @Test
    void aLimitBelowOnePermitIsRejectedRatherThanRefusingEveryRequest() {
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowRateLimiter(0, WINDOW, MANY_KEYS, clock));
    }

    /** A clock that only moves when a test says so. */
}
