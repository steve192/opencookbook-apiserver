package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.PlanScope;

public interface PlanningProfileRepository extends ScopedRepository<PlanningProfile> {

    default List<PlanningProfile> findAllIn(PlanScope scope) {
        return findAllByOwnerAndHouseholdOrderByName(scope.owner(), scope.household());
    }

    List<PlanningProfile> findAllByOwnerAndHouseholdOrderByName(CookpalUser owner, Household household);
}
