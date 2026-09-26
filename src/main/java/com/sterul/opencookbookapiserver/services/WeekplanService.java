package com.sterul.opencookbookapiserver.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;
import com.sterul.opencookbookapiserver.services.access.CookbookAccess;
import com.sterul.opencookbookapiserver.services.households.HouseholdEnding;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class WeekplanService {

    private final WeekplanDayRepository weekplanDayRepository;
    private final CookbookAccess cookbookAccess;

    public WeekplanService(WeekplanDayRepository weekplanDayRepository, CookbookAccess cookbookAccess) {
        this.weekplanDayRepository = weekplanDayRepository;
        this.cookbookAccess = cookbookAccess;
    }

    public List<WeekplanDay> getWeekplanDaysBetweenTime(LocalDate startTime, LocalDate endTime, PlanScope scope) {
        return weekplanDayRepository.findInRange(startTime, endTime, scope);
    }

    /** The stored day, or a new empty one that is saved only once something is planned on it. */
    public WeekplanDay dayOf(LocalDate date, PlanScope scope) {
        return Optional.ofNullable(weekplanDayRepository.findSingleDay(date, scope)).orElseGet(() -> {
            var day = new WeekplanDay();
            scope.assignTo(day);
            day.setPlanDate(date);
            day.setRecipes(new ArrayList<>());
            return day;
        });
    }

    public WeekplanDay updateWeekplanDay(WeekplanDay weekplanDay) {
        log.info("Saving weekplan day {}", weekplanDay.getPlanDate());
        return weekplanDayRepository.save(weekplanDay);
    }

    public List<WeekplanDay> getWeekplanDaysByRecipe(Long id) {
        return weekplanDayRepository.findAllByRecipes_Recipe_Id(id);
    }

    public long countPlannedUses(Long recipeId) {
        return weekplanDayRepository.countPlannedUses(recipeId);
    }

    public List<WeekplanDay> getWeekplanDaysByOwner(CookpalUser user) {
        return weekplanDayRepository.findAllByOwner(user);
    }

    /** Checked on every read, so a meal never leaks its title before the cleanup has removed it. */
    public Predicate<WeekplanDayRecipe> shownIn(PlanScope plan) {
        return openableBy(cookbookAccess.plannableOwnerIds(plan));
    }

    private static Predicate<WeekplanDayRecipe> openableBy(Set<Long> readableOwners) {
        return meal -> meal.isSimpleRecipe() || (meal.getRecipe() != null
                && readableOwners.contains(meal.getRecipe().getOwner().getUserId()));
    }

    /** Removes every meal of the plan that its readers can no longer open. */
    public int removeUnreadableMeals(PlanScope plan) {
        var readableOwners = cookbookAccess.plannableOwnerIds(plan);
        var affected = weekplanDayRepository.findPlanningRecipesOutside(plan, readableOwners);
        for (var day : affected) {
            day.getRecipes().removeIf(openableBy(readableOwners).negate());
            weekplanDayRepository.save(day);
        }
        return affected.size();
    }

    public void deleteWeekplanDay(Long id) {
        log.info("Deleting weekplan day {}", id);
        weekplanDayRepository.deleteById(id);
    }

    @EventListener
    public void deleteWeekOf(HouseholdEnding ending) {
        weekplanDayRepository.deleteAll(weekplanDayRepository.findAllByHouseholdId(ending.householdId()));
    }

}
