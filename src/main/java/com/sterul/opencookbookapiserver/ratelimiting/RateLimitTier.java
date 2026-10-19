package com.sterul.opencookbookapiserver.ratelimiting;

/**
 * What an endpoint costs or risks, and so whose budget it spends; see docs/rate-limits.md. The caller tiers count
 * callers without an account per address and signed-in users per account, each with a budget of their own.
 */
public enum RateLimitTier {

    /** Cheap reads anybody may make. */
    CHEAP,

    /** Endpoints a caller could use to guess a password, a token or an id. */
    GUESSABLE,

    /** Costly work: calls to other services or heavy computing. */
    EXPENSIVE,

    /**
     * Everything behind a share link, which is meant for a few people, not for publishing: counted per link, whoever
     * opens it. The endpoint names the link's path variable with {@link RateLimited#perPathVariable()}.
     */
    SHARE_LINK
}
