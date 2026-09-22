package com.sterul.opencookbookapiserver.controllers.households.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.household.HouseholdInvite;

public record HouseholdInviteResponse(String token, String link, Instant expiresAt) {

    public static HouseholdInviteResponse of(HouseholdInvite invite, String link) {
        return new HouseholdInviteResponse(invite.getId(), link, invite.getExpiresAt());
    }
}
