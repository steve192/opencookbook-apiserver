package com.sterul.opencookbookapiserver.ratelimiting;

/**
 * One budget applied to one request, so that an interceptor does not have to know which of them it
 * is enforcing.
 *
 * @param subject what the budget is kept per, beside the address - a share id, an invite token
 */
@FunctionalInterface
public interface RateLimitedAccessCheck {

    RateLimitDecision check(String clientAddress, String subject);
}
