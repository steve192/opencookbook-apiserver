package com.sterul.opencookbookapiserver.controllers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.sterul.opencookbookapiserver.controllers.requests.WeekplanDayPut;
import com.sterul.opencookbookapiserver.controllers.responses.WeekplanDayResponse;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.WeekplanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/api/v1/weekplan")
@Tag(name = "Weekplan", description = "Managing and fetching weekplans")
public class WeekplanController extends BaseController {

    private final WeekplanService weekplanService;
    private final RecipeService recipeService;
    private final PlanScopes planScopes;

    public WeekplanController(WeekplanService weekplanService, RecipeService recipeService, PlanScopes planScopes,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.weekplanService = weekplanService;
        this.recipeService = recipeService;
        this.planScopes = planScopes;
    }

    @Operation(summary = "Fetch weekplan days in timerange",
            description = "Your own plan, or the household's given. With allPlans, the week across every "
                    + "plan you can see, each day saying which plan it is from.")
    @GetMapping("/{from}/to/{to}")
    public List<WeekplanDayResponse> getBetweenDates(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                     @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) String household,
                                                     // Opt-in, so apps that predate households see their own plan only.
                                                     @RequestParam(defaultValue = "false") boolean allPlans) {
        var user = getLoggedInUser();
        if (!allPlans) {
            return daysOf(planScopes.of(user, household), from, to);
        }
        var days = new ArrayList<WeekplanDayResponse>();
        planScopes.allVisibleTo(user).forEach(scope -> days.addAll(daysOf(scope, from, to)));
        return days;
    }

    @Operation(summary = "Change a single weekplan day",
            description = "Always one plan at a time: reading merges, writing does not.")
    @PutMapping("/{date}")
    public WeekplanDayResponse createAndUpdate(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
                                               @RequestParam(required = false) String household,
                                               @Valid @RequestBody WeekplanDayPut weekplanDayPut) {

        var scope = planScopes.of(getLoggedInUser(), household);
        var weekplanDayEntity = weekplanService.dayOf(date, scope);
        populateWeekplanDayWithRecipes(weekplanDayPut, weekplanDayEntity, scope);
        weekplanDayEntity = weekplanService.updateWeekplanDay(weekplanDayEntity);

        return entityToResponse(weekplanDayEntity, weekplanService.shownIn(scope));
    }

    private List<WeekplanDayResponse> daysOf(PlanScope scope, LocalDate from, LocalDate to) {
        var shown = weekplanService.shownIn(scope);
        return weekplanService.getWeekplanDaysBetweenTime(from, to, scope).stream()
                .map(day -> entityToResponse(day, shown)).toList();
    }

    /** Only meals the plan's readers may open; a stored meal is never proof of access. */
    private WeekplanDayResponse entityToResponse(WeekplanDay weekplanDayEntity, Predicate<WeekplanDayRecipe> shown) {
        var response = new WeekplanDayResponse();
        response.setDay(weekplanDayEntity.getPlanDate());
        var household = weekplanDayEntity.getHousehold();
        if (household != null) {
            response.setHouseholdId(household.getId());
            response.setHouseholdName(household.getName());
        }
        for (var recipe : weekplanDayEntity.getRecipes().stream().filter(shown).toList()) {
            if (recipe.isSimpleRecipe()) {
                var simpleRecipe = new WeekplanDayResponse.SimpleRecipe();
                simpleRecipe.setId(recipe.getId());
                simpleRecipe.setTitle(recipe.getSimpleRecipeText());
                response.getRecipes().add(simpleRecipe);
            } else {
                var normalRecipe = new WeekplanDayResponse.NormalRecipe();
                normalRecipe.setId(recipe.getRecipe().getId());
                normalRecipe.setTitle(recipe.getRecipe().getTitle());
                normalRecipe.setServings(recipe.getServings());
                normalRecipe.setLeftoverOf(recipe.getLeftoverOf());
                if (!recipe.getRecipe().getImages().isEmpty()) {
                    normalRecipe.setTitleImageUuid(recipe.getRecipe().getImages().get(0).getUuid());
                }
                response.getRecipes().add(normalRecipe);
            }
        }
        return response;
    }

    private void populateWeekplanDayWithRecipes(WeekplanDayPut weekplanDayPut, final WeekplanDay newWeekplanDay,
            PlanScope scope) {

        // Built up separately and only swapped in at the end. RecipeService is transactional,
        // so every lookup below commits a transaction of its own and flushes the session that
        // open-session-in-view keeps around. Rebuilding the day's collection in place exposed
        // a half finished collection to that flush.
        var meals = new ArrayList<WeekplanDayRecipe>();

        // Always new entries: reusing the id the client sends hands JPA a detached entity.
        for (var recipe : weekplanDayPut.getRecipes()) {
            switch (recipe.getType()) {
                case NORMAL_RECIPE -> meals.add(
                        plannedMealOf((WeekplanDayPut.NormalRecipe) recipe, newWeekplanDay.getPlanDate(), scope));
                case SIMPLE_RECIPE -> meals.add(
                        WeekplanDayRecipe.simple(((WeekplanDayPut.SimpleRecipe) recipe).getTitle()));
            }
        }

        newWeekplanDay.getRecipes().clear();
        newWeekplanDay.getRecipes().addAll(meals);
    }

    private WeekplanDayRecipe plannedMealOf(WeekplanDayPut.NormalRecipe meal, LocalDate day, PlanScope scope) {
        var recipe = recipeService.getPlannableRecipe(meal.getId(), scope);
        return weekplanService.plannedMeal(recipe, meal.getServings(), meal.getLeftoverOf(), day);
    }
}
