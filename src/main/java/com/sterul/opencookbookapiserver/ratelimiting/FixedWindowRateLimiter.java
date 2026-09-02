package com.sterul.opencookbookapiserver.ratelimiting;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixedWindowRateLimiter {

    private final int permitsPerWindow;
    private final Duration windowLength;
    private final int maxTrackedKeys;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private volatile Instant nextSaturationWarning = Instant.MIN;

    public FixedWindowRateLimiter(int permitsPerWindow, Duration windowLength, int maxTrackedKeys,
            Clock clock) {
        if (permitsPerWindow < 1) {
            throw new IllegalArgumentException("A limit below one permit would refuse everything");
        }
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException("Tracking no keys would refuse everything");
        }
        this.permitsPerWindow = permitsPerWindow;
        this.windowLength = windowLength;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = clock;
    }

    public RateLimitDecision tryAcquire(String key) {
        var now = clock.instant();

        if (!hasRoomFor(key)) {
            warnAboutSaturation(now);
            return RateLimitDecision.allow();
        }

        var window = windows.compute(key, (ignoredKey, currentWindow) ->
                currentWindow == null || currentWindow.hasEnded(now)
                        ? new Window(now.plus(windowLength), 1)
                        : currentWindow.withOneMoreRequest(permitsPerWindow));

        if (window.used() > permitsPerWindow) {
            return RateLimitDecision.refuse(Duration.between(now, window.endsAt()));
        }
        return RateLimitDecision.allow();
    }

    public int evictEndedWindows() {
        var now = clock.instant();
        var sizeBefore = windows.size();
        windows.values().removeIf(window -> window.hasEnded(now));
        return sizeBefore - windows.size();
    }

    private boolean hasRoomFor(String key) {
        if (windows.size() < maxTrackedKeys || windows.containsKey(key)) {
            return true;
        }
        evictEndedWindows();
        return windows.size() < maxTrackedKeys;
    }

    private void warnAboutSaturation(Instant now) {
        if (now.isBefore(nextSaturationWarning)) {
            return;
        }
        nextSaturationWarning = now.plus(windowLength);
        log.warn("Rate limit table is full at {} entries, so requests are no longer being counted."
                + " Something is presenting a great many distinct callers.", maxTrackedKeys);
    }

    private record Window(Instant endsAt, int used) {

        private boolean hasEnded(Instant now) {
            return !endsAt.isAfter(now);
        }

        private Window withOneMoreRequest(int permitsPerWindow) {
            return new Window(endsAt, Math.min(used + 1, permitsPerWindow + 1));
        }
    }
}
