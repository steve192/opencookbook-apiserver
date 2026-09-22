package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.PlanScope;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface WeekplanDayRepository extends JpaRepository<WeekplanDay, Long> {

    default List<WeekplanDay> findInRange(LocalDate from, LocalDate to, PlanScope scope) {
        return findAllByPlanDateBetweenAndOwnerAndHousehold(from, to, scope.owner(), scope.household());
    }

    default WeekplanDay findSingleDay(LocalDate day, PlanScope scope) {
        return findSingleByPlanDateAndOwnerAndHousehold(day, scope.owner(), scope.household());
    }

    /** Days of a plan holding a meal from a cookbook other than these, for the access cleanup. */
    default List<WeekplanDay> findPlanningRecipesOutside(PlanScope scope, Collection<Long> ownerIds) {
        return findDistinctByOwnerAndHouseholdAndRecipes_Recipe_Owner_UserIdNotIn(scope.owner(),
                scope.household(), ownerIds);
    }

    List<WeekplanDay> findAllByPlanDateBetweenAndOwnerAndHousehold(LocalDate dayStart, LocalDate dayEnd,
            CookpalUser owner, Household household);

    WeekplanDay findSingleByPlanDateAndOwnerAndHousehold(LocalDate day, CookpalUser owner, Household household);

    List<WeekplanDay> findDistinctByOwnerAndHouseholdAndRecipes_Recipe_Owner_UserIdNotIn(CookpalUser owner,
            Household household, Collection<Long> ownerIds);

    List<WeekplanDay> findAllByRecipes_Recipe_Id(Long recipeId);

    List<WeekplanDay> findAllByOwner(CookpalUser owner);

    List<WeekplanDay> findAllByHouseholdId(String householdId);

    /** How often a recipe is planned, in any plan. */
    @Query("select count(day) from WeekplanDay day join day.recipes planned where planned.recipe.id = :recipeId")
    long countPlannedUses(@Param("recipeId") Long recipeId);
}
