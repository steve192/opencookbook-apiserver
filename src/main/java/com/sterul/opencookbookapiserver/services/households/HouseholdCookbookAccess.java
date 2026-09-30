package com.sterul.opencookbookapiserver.services.households;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.projections.SharedCookbook;
import com.sterul.opencookbookapiserver.services.access.CookbookAccess;
import com.sterul.opencookbookapiserver.services.access.ReadableCookbooks;

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
    public ReadableCookbooks readableCookbooks(CookpalUser viewer) {
        return new ReadableCookbooks(viewer.getUserId(), memberships.findCookbooksShownTo(viewer).stream()
                .collect(groupingBy(SharedCookbook::ownerId, mapping(SharedCookbook::householdId, toSet()))));
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
