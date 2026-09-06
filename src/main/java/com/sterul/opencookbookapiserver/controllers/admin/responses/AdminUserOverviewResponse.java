package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.services.UserService.UserHoldings;

/** One row of the account list: the account, plus the counts only that list asks for. */
public record AdminUserOverviewResponse(
        Long userId,
        String emailAddress,
        boolean activated,
        Role roles,
        Instant createdOn,
        Instant lastChange,
        long recipeCount,
        long ingredientCount) {

    public static AdminUserOverviewResponse fromHoldings(UserHoldings holdings) {
        var user = holdings.user();
        return new AdminUserOverviewResponse(
                user.getUserId(),
                user.getEmailAddress(),
                user.isActivated(),
                user.getRoles(),
                user.getCreatedOn(),
                user.getLastChange(),
                holdings.recipeCount(),
                holdings.ingredientCount());
    }
}
