package com.sterul.opencookbookapiserver.services.households;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.ratelimiting.EvictableRateLimits;
import com.sterul.opencookbookapiserver.ratelimiting.FixedWindowRateLimiter;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;

/** Per address only: the token is what is being guessed, so a per-token budget would not slow a walk. */
@Component
public class HouseholdInviteRateLimiter implements EvictableRateLimits {

    private static final Duration WINDOW = Duration.ofHours(1);

    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final FixedWindowRateLimiter lookupsPerAddress;

    public HouseholdInviteRateLimiter(OpencookbookConfiguration configuration, Clock clock) {
        this.lookupsPerAddress = new FixedWindowRateLimiter(
                configuration.getHouseholds().getInviteLookupsPerHourPerIp(), WINDOW, MAX_TRACKED_CALLERS, clock);
    }

    /**
     * The token goes unread. Lookups are counted per address alone, but the rate limit filter
     * reaches this through a function type that passes the path variable with it.
     */
    @SuppressWarnings("java:S1172")
    public RateLimitDecision recordLookup(String clientAddress, String token) {
        return lookupsPerAddress.tryAcquire(clientAddress);
    }

    @Override
    public int evictEndedWindows() {
        return lookupsPerAddress.evictEndedWindows();
    }
}
