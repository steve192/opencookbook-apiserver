package com.sterul.opencookbookapiserver.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.ScopedEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;

/** Rows that belong to a person or a household, looked up within one scope. */
@NoRepositoryBean
public interface ScopedRepository<T extends ScopedEntity> extends JpaRepository<T, Long> {

    /** Another scope's row is simply not found, so ids cannot be probed. */
    default Optional<T> findIn(Long id, PlanScope scope) {
        return findByIdAndOwnerAndHousehold(id, scope.owner(), scope.household());
    }

    Optional<T> findByIdAndOwnerAndHousehold(Long id, CookpalUser owner, Household household);
}
