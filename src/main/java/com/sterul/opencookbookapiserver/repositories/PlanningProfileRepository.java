package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.PlanScope;

public interface PlanningProfileRepository extends JpaRepository<PlanningProfile, Long> {

    default List<PlanningProfile> findAllIn(PlanScope scope) {
        return findAllByOwnerAndHouseholdOrderByName(scope.owner(), scope.household());
    }

    /** Somebody else's profile is simply not found, so ids cannot be probed. */
    default Optional<PlanningProfile> findIn(Long id, PlanScope scope) {
        return findByIdAndOwnerAndHousehold(id, scope.owner(), scope.household());
    }

    List<PlanningProfile> findAllByOwnerAndHouseholdOrderByName(CookpalUser owner, Household household);

    Optional<PlanningProfile> findByIdAndOwnerAndHousehold(Long id, CookpalUser owner, Household household);
}
