package com.sterul.opencookbookapiserver.ratelimiting;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixedWindowRateLimiter {

    private final int permitsPerWindow;
    private final Duration windowLength;
    private final Clock clock;

    /**
     * Least recently used first, so a full table drops the caller nobody has heard from in
     * longest rather than giving up and letting everything through. Guarded by itself.
     */
    private final Map<String, Window> windows;

    private Instant nextSaturationWarning = Instant.MIN;

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
        this.clock = clock;
        this.windows = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
                if (size() <= maxTrackedKeys) {
                    return false;
                }
                warnAboutSaturation(maxTrackedKeys);
                return true;
            }
        };
    }

    public RateLimitDecision tryAcquire(String key) {
        var now = clock.instant();

        Window window;
        synchronized (windows) {
            var current = windows.get(key);
            window = current == null || current.hasEnded(now)
                    ? new Window(now.plus(windowLength), 1)
                    : current.withOneMoreRequest(permitsPerWindow);
            windows.put(key, window);
        }

        if (window.used() > permitsPerWindow) {
            return RateLimitDecision.refuse(Duration.between(now, window.endsAt()));
        }
        return RateLimitDecision.allow();
    }

    public int evictEndedWindows() {
        var now = clock.instant();
        synchronized (windows) {
            var sizeBefore = windows.size();
            windows.values().removeIf(window -> window.hasEnded(now));
            return sizeBefore - windows.size();
        }
    }

    private void warnAboutSaturation(int maxTrackedKeys) {
        var now = clock.instant();
        if (now.isBefore(nextSaturationWarning)) {
            return;
        }
        nextSaturationWarning = now.plus(windowLength);
        log.warn("Rate limit table is full at {} entries, so the least recently seen callers are"
                + " being forgotten. Something is presenting a great many distinct callers.",
                maxTrackedKeys);
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
