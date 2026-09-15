package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.util.List;

/**
 * @param values per serving, or for the whole recipe without servings
 * @param lines  contributions for the whole recipe
 */
public record RecipeNutrition(Basis basis, Status status, NutrientAmounts values, int warningCount, List<LineNutrition> lines) {

    public RecipeNutrition {
        lines = List.copyOf(lines);
    }

    public enum Basis {
        SERVING, RECIPE
    }

    public enum Status {
        COMPLETE,
        /** Shown with a warning. */
        INCOMPLETE,
        /** Too uncertain to show. */
        UNAVAILABLE
    }
}
