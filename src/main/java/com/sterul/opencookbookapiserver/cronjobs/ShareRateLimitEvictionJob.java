package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.services.sharing.ShareAccessRateLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * Drops rate limit counters whose window has passed.
 */
@EnableScheduling
@Configuration
@Slf4j
public class ShareRateLimitEvictionJob {

    private static final long ONE_HOUR_IN_MILLISECONDS = 60L * 60L * 1000L;

    private final ShareAccessRateLimiter rateLimiter;

    public ShareRateLimitEvictionJob(ShareAccessRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(fixedRate = ONE_HOUR_IN_MILLISECONDS)
    public void evictEndedWindows() {
        var evicted = rateLimiter.evictEndedWindows();
        if (evicted > 0) {
            log.info("Evicted {} ended share rate limit windows", evicted);
        }
    }
}
