package com.sterul.opencookbookapiserver.services.access;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/**
 * Whose cookbooks a viewer may read: the read half of the access rule. Answered as owner ids so
 * list queries can narrow in SQL.
 */
public interface CookbookAccess {

    /** Always contains the viewer. */
    Set<Long> visibleOwnerIds(CookpalUser viewer);

    /** Whose recipes every reader of this plan can open; for a household, its cookbook. */
    Set<Long> plannableOwnerIds(PlanScope plan);

    /**
     * What a suggestion or a generated week draws on: a person's own cookbook, or everything they
     * may read when they ask for it; a household's own cookbook, whatever is asked.
     */
    default Set<Long> poolOwnerIds(PlanScope plan, boolean includeHouseholds) {
        return switch (plan) {
            case PlanScope.Personal(var user) -> includeHouseholds ? visibleOwnerIds(user) : Set.of(user.getUserId());
            case PlanScope.OfHousehold ignored -> plannableOwnerIds(plan);
        };
    }

    /** In how many households this owner's cookbook is shown. */
    long householdsShowing(CookpalUser owner);
}
