package com.sterul.opencookbookapiserver.controllers.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.households.HouseholdMembershipService;

import jakarta.annotation.Nullable;

/** Resolves the {@code ?household=} of a plan endpoint; the only place such a request is authorised. */
@Component
public class PlanScopes {

    private final HouseholdMembershipService householdMemberships;
    private final OpencookbookConfiguration configuration;

    public PlanScopes(HouseholdMembershipService householdMemberships, OpencookbookConfiguration configuration) {
        this.householdMemberships = householdMemberships;
        this.configuration = configuration;
    }

    /** No household means the user's own plan; one they are not a member of is "not found". */
    public PlanScope of(CookpalUser user, @Nullable String householdId) throws ElementNotFound {
        if (householdId == null || householdId.isBlank()) {
            return PlanScope.of(user);
        }
        if (!householdsEnabled()) {
            throw new ElementNotFound();
        }
        return PlanScope.of(householdMemberships.requireMembership(householdId, user).getHousehold());
    }

    /** Every plan the user can see, their own first. */
    public List<PlanScope> allVisibleTo(CookpalUser user) {
        var scopes = new ArrayList<PlanScope>();
        scopes.add(PlanScope.of(user));
        if (householdsEnabled()) {
            householdMemberships.householdsOf(user)
                    .forEach(membership -> scopes.add(PlanScope.of(membership.getHousehold())));
        }
        return scopes;
    }

    private boolean householdsEnabled() {
        return configuration.getHouseholds().isEnabled();
    }
}
