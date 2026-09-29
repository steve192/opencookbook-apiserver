package com.sterul.opencookbookapiserver.services.shopping.preview;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
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
    private final StapleService stapleService;
    private final PreviewLines previewLines;

    public ImportPreviewService(WeekplanService weekplanService, RecipeService recipeService,
            StapleService stapleService, PreviewLines previewLines) {
        this.weekplanService = weekplanService;
        this.recipeService = recipeService;
        this.stapleService = stapleService;
        this.previewLines = previewLines;
    }

    public List<PreviewMeal> forWeek(PlanScope scope, LocalDate from, LocalDate to, CookpalUser viewer) {
        var shown = weekplanService.shownIn(scope);
        var staples = stapleService.stapleKeysOf(viewer);
        return weekplanService.getWeekplanDaysBetweenTime(from, to, scope).stream()
                .sorted(Comparator.comparing(WeekplanDay::getPlanDate))
                .flatMap(day -> day.getRecipes().stream().filter(shown)
                        .map(meal -> mealOf(day.getPlanDate(), meal, viewer, staples)))
                .toList();
    }

    public PreviewMeal forRecipe(Long recipeId, CookpalUser viewer) {
        var recipe = recipeService.getRecipeFor(recipeId, viewer);
        return PreviewMeal.cooked(String.valueOf(recipe.getId()), null, recipe, recipe.writtenServings(),
                previewLines.of(recipe, viewer, stapleService.stapleKeysOf(viewer)));
    }

    private PreviewMeal mealOf(LocalDate date, WeekplanDayRecipe meal, CookpalUser viewer, Set<String> staples) {
        if (meal.isSimpleRecipe()) {
            return PreviewMeal.spontaneous(meal.getId(), date, meal.getSimpleRecipeText());
        }
        var recipe = meal.getRecipe();
        if (meal.getLeftoverOf() != null) {
            return PreviewMeal.leftover(meal.getId(), date, recipe, meal.getLeftoverOf());
        }
        return PreviewMeal.cooked(meal.getId(), date, recipe, meal.getServings(),
                previewLines.of(recipe, viewer, staples));
    }
}
