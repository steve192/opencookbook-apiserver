package com.sterul.opencookbookapiserver.configurations.sharing;

import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;

/**
 * One budget applied to one request, so that the interceptor does not have to know which of them
 * it is enforcing.
 */
@FunctionalInterface
public interface ShareAccessCheck {

    RateLimitDecision check(String clientAddress, String shareId);
}
