package com.sterul.opencookbookapiserver.services.recipeimport.instagram;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.ratelimiting.EvictableRateLimits;
import com.sterul.opencookbookapiserver.ratelimiting.FixedWindowRateLimiter;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;

@Component
public class InstagramReadRateLimiter implements EvictableRateLimits {

    private static final Duration WINDOW = Duration.ofHours(1);

    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final FixedWindowRateLimiter readsPerUser;

    public InstagramReadRateLimiter(OpencookbookConfiguration configuration, Clock clock) {
        this.readsPerUser = new FixedWindowRateLimiter(
                configuration.getRecipeImport().getInstagramReadsPerHourPerUser(), WINDOW, MAX_TRACKED_CALLERS, clock);
    }

    public RateLimitDecision recordRead(CookpalUser reader) {
        return readsPerUser.tryAcquire(String.valueOf(reader.getUserId()));
    }

    @Override
    public int evictEndedWindows() {
        return readsPerUser.evictEndedWindows();
    }
}
