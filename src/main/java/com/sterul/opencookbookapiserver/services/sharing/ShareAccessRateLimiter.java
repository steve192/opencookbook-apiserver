package com.sterul.opencookbookapiserver.services.sharing;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.ratelimiting.FixedWindowRateLimiter;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;

@Component
public class ShareAccessRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final FixedWindowRateLimiter recipeViewsPerAddress;
    private final FixedWindowRateLimiter recipeViewsPerShare;
    private final FixedWindowRateLimiter imageViewsPerAddress;

    public ShareAccessRateLimiter(OpencookbookConfiguration configuration, Clock clock) {
        var sharing = configuration.getSharing();
        this.recipeViewsPerAddress = new FixedWindowRateLimiter(
                sharing.getViewsPerHourPerIp(), WINDOW, MAX_TRACKED_CALLERS, clock);
        this.recipeViewsPerShare = new FixedWindowRateLimiter(
                sharing.getViewsPerHourPerShare(), WINDOW, MAX_TRACKED_CALLERS, clock);
        this.imageViewsPerAddress = new FixedWindowRateLimiter(
                sharing.getImageViewsPerHourPerIp(), WINDOW, MAX_TRACKED_CALLERS, clock);
    }

    public RateLimitDecision recordRecipeView(String clientAddress, String shareId) {
        var addressDecision = recipeViewsPerAddress.tryAcquire(clientAddress);
        if (!addressDecision.allowed()) {
            return addressDecision;
        }
        return recipeViewsPerShare.tryAcquire(shareId);
    }

    public RateLimitDecision recordImageView(String clientAddress, String shareId) {
        return imageViewsPerAddress.tryAcquire(clientAddress);
    }

    public int evictEndedWindows() {
        return recipeViewsPerAddress.evictEndedWindows()
                + recipeViewsPerShare.evictEndedWindows()
                + imageViewsPerAddress.evictEndedWindows();
    }
}
