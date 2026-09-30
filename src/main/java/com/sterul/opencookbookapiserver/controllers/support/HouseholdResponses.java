package com.sterul.opencookbookapiserver.controllers.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.controllers.households.responses.HouseholdResponse;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.households.HouseholdMembershipService;

@Component
public class HouseholdResponses {

    private final HouseholdMembershipService memberships;

    public HouseholdResponses(HouseholdMembershipService memberships) {
        this.memberships = memberships;
    }

    public List<HouseholdResponse> summariesFor(CookpalUser viewer) {
        return memberships.householdsOf(viewer).stream()
                .map(mine -> HouseholdResponse.summaryOf(mine,
                        memberships.memberCountOf(mine.getHousehold().getId())))
                .toList();
    }

    public HouseholdResponse detailFor(String householdId, CookpalUser viewer) {
        var mine = memberships.requireMembership(householdId, viewer);
        return HouseholdResponse.detailOf(mine, memberships.membersOf(householdId));
    }
}
