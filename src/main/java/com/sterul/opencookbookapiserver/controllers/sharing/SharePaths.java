package com.sterul.opencookbookapiserver.controllers.sharing;

/**
 * Where the sharing endpoints live.
 *
 * These are constants rather than literals repeated in each place because the controllers and the
 * security whitelist have to agree on them, and the whitelist matches on path alone: it cannot
 * express a method, so the split between what is public and what is not is carried entirely by
 * these two prefixes. Widening the public one by a single character would publish
 * revoking and importing along with reading.
 */
public final class SharePaths {

    /** Read only, served without authentication. Nothing mapped under here may write. */
    public static final String PUBLIC_BASE = "/api/v1/shared";

    /** What the security whitelist opens up. */
    public static final String PUBLIC_PATTERN = PUBLIC_BASE + "/**";

    /** Managing your own shares, and importing somebody else's. Always authenticated. */
    public static final String OWNER_BASE = "/api/v1/shares";

    /** Name of the share id path variable; each share has a rate limit budget of its own. */
    public static final String SHARE_ID_VARIABLE = "shareId";

    private SharePaths() {
    }
}
