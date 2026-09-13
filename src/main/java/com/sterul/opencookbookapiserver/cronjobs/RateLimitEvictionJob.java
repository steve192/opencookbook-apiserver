package com.sterul.opencookbookapiserver.cronjobs;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.ratelimiting.EvictableRateLimits;

import lombok.extern.slf4j.Slf4j;

/** Drops rate limit counters whose window has passed, for every limiter there is. */
@EnableScheduling
@Configuration
@Slf4j
public class RateLimitEvictionJob {

    private static final long ONE_HOUR_IN_MILLISECONDS = 60L * 60L * 1000L;

    private final List<EvictableRateLimits> rateLimiters;

    public RateLimitEvictionJob(List<EvictableRateLimits> rateLimiters) {
        this.rateLimiters = rateLimiters;
    }

    @Scheduled(fixedRate = ONE_HOUR_IN_MILLISECONDS)
    public void evictEndedWindows() {
        var evicted = rateLimiters.stream().mapToInt(EvictableRateLimits::evictEndedWindows).sum();
        if (evicted > 0) {
            log.info("Evicted {} ended rate limit windows", evicted);
        }
    }
}
