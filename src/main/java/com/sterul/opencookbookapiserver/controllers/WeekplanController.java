package com.sterul.opencookbookapiserver.controllers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller
@RequestMapping("/api/v1/weekplan")
@Tag(name = "Weekplan", description = "Managing and fetching weekplans")
public class WeekplanController extends BaseController {

    @Autowired
    WeekplanService weekplanService;
    @Autowired
    RecipeService recipeService;
    @Autowired
    PlanScopes planScopes;

    @Operation(summary = "Fetch weekplan days in timerange",
            description = "Your own plan, or the household's given. With allPlans, the week across every "
                    + "plan you can see, each day saying which plan it is from.")
    @GetMapping("/{from}/to/{to}")
    public List<WeekplanDayResponse> getBetweenDates(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                     @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) String household,
                                                     // Opt-in, so apps that predate households see their own plan only.
                                                     @RequestParam(defaultValue = "false") boolean allPlans)
            throws ElementNotFound {
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
                                               @RequestBody WeekplanDayPut weekplanDayPut) throws ElementNotFound {

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
                if (!recipe.getRecipe().getImages().isEmpty()) {
                    normalRecipe.setTitleImageUuid(recipe.getRecipe().getImages().get(0).getUuid());
                }
                response.getRecipes().add(normalRecipe);
            }
        }
        return response;
    }

    private void populateWeekplanDayWithRecipes(WeekplanDayPut weekplanDayPut, final WeekplanDay newWeekplanDay,
            PlanScope scope)
            throws ElementNotFound {

        // Built up separately and only swapped in at the end. RecipeService is transactional,
        // so every lookup below commits a transaction of its own and flushes the session that
        // open-session-in-view keeps around. Rebuilding the day's collection in place exposed
        // a half finished collection to that flush.
        var meals = new ArrayList<WeekplanDayRecipe>();

        for (var recipe : weekplanDayPut.getRecipes()) {
            switch (recipe.getType()) {
                case NORMAL_RECIPE -> {
                    var recipeId = ((WeekplanDayPut.NormalRecipe) recipe).getId();

                    var recipeEntity = recipeService.getPlannableRecipe(recipeId, scope);
                    meals.add(WeekplanDayRecipe.builder()
                            .isSimpleRecipe(false)
                            .recipe(recipeEntity)
                            .build());
                }
                case SIMPLE_RECIPE -> {
                    var simpleRecipe = (WeekplanDayPut.SimpleRecipe) recipe;

                    // The id the client sends back identifies a row this request replaces.
                    // Carrying it into a new instance handed JPA an entity it considers
                    // detached, so every entry of the day is created fresh and the rows it
                    // replaces are removed as orphans.
                    meals.add(WeekplanDayRecipe.builder()
                            .isSimpleRecipe(true)
                            .simpleRecipeText(simpleRecipe.getTitle())
                            .build());
                }
            }
        }

        newWeekplanDay.getRecipes().clear();
        newWeekplanDay.getRecipes().addAll(meals);
    }
}
