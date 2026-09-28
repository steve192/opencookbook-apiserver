package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;

import jakarta.persistence.LockModeType;

public interface ShoppingListRepository extends ScopedRepository<ShoppingList> {

    default List<ShoppingList> findAllIn(PlanScope scope) {
        return findAllByOwnerAndHouseholdOrderByDefaultListDescNameAsc(scope.owner(), scope.household());
    }

    /** Serialises writers of one list, so versions never repeat. */
    default Optional<ShoppingList> lockIn(Long id, PlanScope scope) {
        return findLockedByIdAndOwnerAndHousehold(id, scope.owner(), scope.household());
    }

    default Optional<ShoppingList> findDefaultIn(PlanScope scope) {
        return findByOwnerAndHouseholdAndDefaultListTrue(scope.owner(), scope.household());
    }

    default long countIn(PlanScope scope) {
        return countByOwnerAndHousehold(scope.owner(), scope.household());
    }

    List<ShoppingList> findAllByOwnerAndHouseholdOrderByDefaultListDescNameAsc(CookpalUser owner, Household household);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingList> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingList> findLockedByIdAndOwnerAndHousehold(Long id, CookpalUser owner, Household household);

    Optional<ShoppingList> findByOwnerAndHouseholdAndDefaultListTrue(CookpalUser owner, Household household);

    long countByOwnerAndHousehold(CookpalUser owner, Household household);
}
