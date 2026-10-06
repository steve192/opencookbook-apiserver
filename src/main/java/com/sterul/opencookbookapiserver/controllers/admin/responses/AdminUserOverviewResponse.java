package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.services.UserService.UserHoldings;

/**
 * One row of the account list: the account, plus what only that list asks for.
 *
 * @param lastActiveAt null when not used since signing up
 * @param lastSignInAt null when not signed in since this was recorded
 */
public record AdminUserOverviewResponse(
        Long userId,
        String emailAddress,
        boolean activated,
        Role roles,
        Instant createdOn,
        Instant lastChange,
        Instant lastActiveAt,
        Instant lastSignInAt,
        List<SignInMethod> signInMethods,
        long recipeCount,
        long ingredientCount) {

    public enum SignInMethod {
        PASSWORD, GOOGLE
    }

    public static AdminUserOverviewResponse fromHoldings(UserHoldings holdings) {
        var user = holdings.user();
        return new AdminUserOverviewResponse(
                user.getUserId(),
                user.getEmailAddress(),
                user.isActivated(),
                user.getRoles(),
                user.getCreatedOn(),
                user.getLastChange(),
                user.getLastActiveAt(),
                user.getLastSignInAt(),
                signInMethodsOf(user),
                holdings.recipeCount(),
                holdings.ingredientCount());
    }

    private static List<SignInMethod> signInMethodsOf(CookpalUser user) {
        var methods = new ArrayList<SignInMethod>();
        if (user.getPasswordHash() != null) {
            methods.add(SignInMethod.PASSWORD);
        }
        if (user.isGoogleLinked()) {
            methods.add(SignInMethod.GOOGLE);
        }
        return methods;
    }
}
