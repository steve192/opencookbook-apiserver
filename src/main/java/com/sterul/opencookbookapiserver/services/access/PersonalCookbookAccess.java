package com.sterul.opencookbookapiserver.services.access;

import java.util.Map;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/** Your own cookbook and nothing else: the rule while households are switched off. */
public class PersonalCookbookAccess implements CookbookAccess {

    @Override
    public ReadableCookbooks readableCookbooks(CookpalUser viewer) {
        return new ReadableCookbooks(viewer.getUserId(), Map.of());
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
