package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;

/** The account itself. What it happens to own is the overview's business, not this one's. */
public record AdminUserResponse(
        Long userId,
        String emailAddress,
        boolean activated,
        Role roles,
        Instant createdOn,
        Instant lastChange) {

    public static AdminUserResponse fromEntity(CookpalUser user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getEmailAddress(),
                user.isActivated(),
                user.getRoles(),
                user.getCreatedOn(),
                user.getLastChange());
    }
}
