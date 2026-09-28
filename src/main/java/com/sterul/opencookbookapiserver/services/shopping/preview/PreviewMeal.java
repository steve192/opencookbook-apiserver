package com.sterul.opencookbookapiserver.services.shopping.preview;

import java.time.LocalDate;
import java.util.List;

/**
 * @param entryId the weekplan entry, or the recipe when imported from the recipe screen
 * @param date null for a recipe imported from the recipe screen
 * @param recipeServings what the lines are written for
 * @param defaultServings what to shop for unless changed
 * @param lines empty for a meal planned without a recipe
 */
public record PreviewMeal(String entryId, LocalDate date, String title, Long recipeId, int recipeServings,
        int defaultServings, List<PreviewLine> lines) {

    public boolean isSpontaneous() {
        return recipeId == null;
    }
}
