package com.sterul.opencookbookapiserver.services.shopping.preview;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.planning.PlanningProfileService;
import com.sterul.opencookbookapiserver.services.shopping.StapleService;

/**
 * What an import sheet offers: the meals, and each meal's ingredients unscaled, so toggling a meal or
 * changing its servings needs no request.
 */
@Service
@Transactional(readOnly = true)
public class ImportPreviewService {

    private final WeekplanService weekplanService;
    private final RecipeService recipeService;
    private final PlanningProfileService profileService;
    private final StapleService stapleService;
    private final PreviewLines previewLines;

    public ImportPreviewService(WeekplanService weekplanService, RecipeService recipeService,
            PlanningProfileService profileService, StapleService stapleService, PreviewLines previewLines) {
        this.weekplanService = weekplanService;
        this.recipeService = recipeService;
        this.profileService = profileService;
        this.stapleService = stapleService;
        this.previewLines = previewLines;
    }

    /**
     * Each planned recipe is shopped for the plan's household size, as the week generator cooks it; a
     * leftover it planned is a second entry of the same recipe, so the extra servings are bought too.
     */
    public List<PreviewMeal> forWeek(PlanScope scope, LocalDate from, LocalDate to, CookpalUser viewer) {
        var shown = weekplanService.shownIn(scope);
        var householdSize = profileService.householdSizeOf(scope);
        var staples = stapleService.stapleKeysOf(viewer);
        return weekplanService.getWeekplanDaysBetweenTime(from, to, scope).stream()
                .sorted(Comparator.comparing(WeekplanDay::getPlanDate))
                .flatMap(day -> day.getRecipes().stream().filter(shown)
                        .map(meal -> mealOf(day, meal, householdSize, viewer, staples)))
                .toList();
    }

    public PreviewMeal forRecipe(Long recipeId, CookpalUser viewer) {
        var recipe = recipeService.getRecipeFor(recipeId, viewer);
        var servings = servingsOf(recipe);
        return new PreviewMeal(String.valueOf(recipe.getId()), null, recipe.getTitle(), recipe.getId(), servings,
                servings, previewLines.of(recipe, viewer, stapleService.stapleKeysOf(viewer)));
    }

    private PreviewMeal mealOf(WeekplanDay day, WeekplanDayRecipe meal, OptionalInt householdSize,
            CookpalUser viewer, Set<String> staples) {
        if (meal.isSimpleRecipe()) {
            return new PreviewMeal(meal.getId(), day.getPlanDate(), meal.getSimpleRecipeText(), null, 0, 0, List.of());
        }
        var recipe = meal.getRecipe();
        var servings = servingsOf(recipe);
        return new PreviewMeal(meal.getId(), day.getPlanDate(), recipe.getTitle(), recipe.getId(), servings,
                householdSize.orElse(servings), previewLines.of(recipe, viewer, staples));
    }

    /** A recipe that states no servings is shopped for as written. */
    private static int servingsOf(Recipe recipe) {
        return Math.max(1, recipe.getServings());
    }
}
