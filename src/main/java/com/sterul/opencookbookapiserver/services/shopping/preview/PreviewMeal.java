package com.sterul.opencookbookapiserver.services.shopping.preview;

import java.time.LocalDate;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * @param entryId the weekplan entry, or the recipe when imported from the recipe screen
 * @param date null for a recipe imported from the recipe screen
 * @param recipeServings what the lines are written for
 * @param defaultServings what to shop for unless changed
 * @param leftoverOf the day the eaten meal was cooked; nothing is bought for leftovers
 * @param lines empty for a meal planned without a recipe and for leftovers
 */
public record PreviewMeal(String entryId, LocalDate date, String title, Long recipeId, int recipeServings,
        int defaultServings, LocalDate leftoverOf, List<PreviewLine> lines) {

    static PreviewMeal cooked(String entryId, LocalDate date, Recipe recipe, int servings, List<PreviewLine> lines) {
        return new PreviewMeal(entryId, date, recipe.getTitle(), recipe.getId(), recipe.writtenServings(), servings,
                null, lines);
    }

    static PreviewMeal leftover(String entryId, LocalDate date, Recipe recipe, LocalDate cookedOn) {
        return new PreviewMeal(entryId, date, recipe.getTitle(), recipe.getId(), 0, 0, cookedOn, List.of());
    }

    static PreviewMeal spontaneous(String entryId, LocalDate date, String title) {
        return new PreviewMeal(entryId, date, title, null, 0, 0, null, List.of());
    }

    public boolean isSpontaneous() {
        return recipeId == null;
    }
}
