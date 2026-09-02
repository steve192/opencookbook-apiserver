package com.sterul.opencookbookapiserver.ratelimiting;

import java.time.Duration;

/**
 * The answer to "may this request go ahead". {@code retryAfter} is meaningful only when refused.
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, Duration.ZERO);

    public static RateLimitDecision allow() {
        return ALLOWED;
    }

    public static RateLimitDecision refuse(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
