package com.sterul.opencookbookapiserver.controllers.households;

/** Shared by the controllers and the security rules. None of these is public. */
public final class HouseholdPaths {

    public static final String BASE = "/api/v1/households";

    public static final String INVITE_BASE = "/api/v1/household-invites";

    private HouseholdPaths() {
    }
}
