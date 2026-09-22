package com.sterul.opencookbookapiserver.controllers.households;

/** Shared by the controllers and the invite rate limit registration. None of these is public. */
public final class HouseholdPaths {

    public static final String BASE = "/api/v1/households";

    public static final String INVITE_BASE = "/api/v1/household-invites";

    public static final String INVITE_PATTERN = INVITE_BASE + "/**";

    public static final String INVITE_TOKEN_VARIABLE = "token";

    private HouseholdPaths() {
    }
}
