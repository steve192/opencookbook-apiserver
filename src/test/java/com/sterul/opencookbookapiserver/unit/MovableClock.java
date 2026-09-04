package com.sterul.opencookbookapiserver.unit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock the tests move by hand.
 *
 * Anything with a window, a timeout or a daily allowance is otherwise only testable by
 * waiting, and the production spans are hours or days long.
 */
public final class MovableClock extends Clock {

    private Instant now;

    public MovableClock(Instant startingAt) {
        this.now = startingAt;
    }

    public void advanceBy(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneOffset getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The tests only ever need UTC");
    }
}
