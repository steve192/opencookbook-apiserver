package com.sterul.opencookbookapiserver.services.access;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/** Your own cookbook and nothing else: the rule while households are switched off. */
public class PersonalCookbookAccess implements CookbookAccess {

    @Override
    public Set<Long> visibleOwnerIds(CookpalUser viewer) {
        return Set.of(viewer.getUserId());
    }

    @Override
    public Set<Long> plannableOwnerIds(PlanScope plan) {
        return switch (plan) {
            case PlanScope.Personal(var user) -> visibleOwnerIds(user);
            case PlanScope.OfHousehold ignored -> Set.of();
        };
    }

    @Override
    public long householdsShowing(CookpalUser owner) {
        return 0;
    }
}
