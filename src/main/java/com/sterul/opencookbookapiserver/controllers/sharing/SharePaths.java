package com.sterul.opencookbookapiserver.controllers.sharing;

/**
 * Where the sharing endpoints live.
 *
 * These are constants rather than literals repeated in each place because three separate things
 * have to agree on them and only one of the three fails loudly when they do not: the controllers,
 * the rate limit registration, and the security whitelist. The whitelist matches on path alone -
 * it cannot express a method - so the split between what is public and what is not is carried
 * entirely by these two prefixes. Widening the public one by a single character would publish
 * revoking and importing along with reading.
 */
public final class SharePaths {

    /** Read only, served without authentication. Nothing mapped under here may write. */
    public static final String PUBLIC_BASE = "/api/v1/shared";

    /** What the security whitelist opens up. */
    public static final String PUBLIC_PATTERN = PUBLIC_BASE + "/**";

    /** A shared recipe itself: exactly one path segment, so it never matches an image below it. */
    public static final String PUBLIC_RECIPE_PATTERN = PUBLIC_BASE + "/*";

    /** The images of a shared recipe. */
    public static final String PUBLIC_IMAGE_PATTERN = PUBLIC_BASE + "/*/images/**";

    /** Managing your own shares, and importing somebody else's. Always authenticated. */
    public static final String OWNER_BASE = "/api/v1/shares";

    /** Name of the share id path variable, shared with the rate limit interceptor. */
    public static final String SHARE_ID_VARIABLE = "shareId";

    private SharePaths() {
    }
}
