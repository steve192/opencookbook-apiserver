package com.sterul.opencookbookapiserver.services.households;

import java.util.HashSet;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.services.access.CookbookAccess;

/**
 * Your own cookbook, plus those of everyone sharing one with a household you are in.
 *
 * Deliberately uncached: a cache outliving one request keeps a removed member reading.
 */
public class HouseholdCookbookAccess implements CookbookAccess {

    private final HouseholdMembershipRepository memberships;

    public HouseholdCookbookAccess(HouseholdMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public Set<Long> visibleOwnerIds(CookpalUser viewer) {
        var visible = new HashSet<>(memberships.findOwnerIdsVisibleTo(viewer));
        visible.add(viewer.getUserId());
        return visible;
    }

    @Override
    public Set<Long> plannableOwnerIds(PlanScope plan) {
        return switch (plan) {
            case PlanScope.Personal(var user) -> visibleOwnerIds(user);
            case PlanScope.OfHousehold(var household) -> memberships.findSharingMemberIds(household.getId());
        };
    }

    @Override
    public long householdsShowing(CookpalUser owner) {
        return memberships.countByMemberAndShareRecipesTrue(owner);
    }
}
