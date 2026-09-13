package com.sterul.opencookbookapiserver.ratelimiting;

/**
 * A holder of rate limit counters that can be swept.
 *
 * Implemented rather than registered, so that a limiter added later is swept by the existing job
 * without anybody remembering to say so.
 */
public interface EvictableRateLimits {

    /**
     * @return how many windows whose time has passed were dropped
     */
    int evictEndedWindows();
}
