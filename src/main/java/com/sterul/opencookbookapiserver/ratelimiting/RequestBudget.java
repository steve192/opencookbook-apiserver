package com.sterul.opencookbookapiserver.ratelimiting;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

/**
 * So many requests per period for each key, such as an address, an account or a link: a token bucket per key that
 * holds a period's permits and refills them evenly over the period.
 */
public class RequestBudget {

    /** Beyond this, the cache drops the keys least likely to come back rather than refusing new ones. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int permits;
    private final Duration period;
    private final TimeMeter time;
    private final Cache<String, Bucket> buckets;

    public RequestBudget(int permits, Duration period, Clock clock) {
        this.permits = permits;
        this.period = period;
        this.time = new ClockTime(clock);
        // A bucket nobody touched for a whole period is full again, so forgetting it loses nothing.
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(period)
                .maximumSize(MAX_TRACKED_KEYS)
                .ticker(time::currentTimeNanos)
                .build();
    }

    public RateLimitDecision spend(String key) {
        var probe = buckets.get(key, this::newBucket).tryConsumeAndReturnRemaining(1);
        return probe.isConsumed()
                ? RateLimitDecision.allow()
                : RateLimitDecision.refuse(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket(String key) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(permits).refillGreedy(permits, period))
                .withCustomTimePrecision(time)
                .build();
    }

    /** The application's clock, so tests can move time. */
    private record ClockTime(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            var now = clock.instant();
            return TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
