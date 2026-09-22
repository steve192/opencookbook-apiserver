package com.sterul.opencookbookapiserver.controllers.requests;

import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.services.selection.Jitter;
import com.sterul.opencookbookapiserver.services.suggestion.MatchMode;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Everything is optional: a request answering nothing is a valid "surprise me".
 *
 * @param seed repeat a previous seed to get that result set back; leave it out for a new draw
 * @param includeHouseholdRecipes also draw on the household cookbooks you may read; left out means no
 */
public record RecipeSuggestionRequest(
        MatchMode mode,
        @Size(max = MAX_INGREDIENTS) List<Long> ingredientIds,
        @Positive Long maxTotalTimeMinutes,
        Diet diet,
        Set<MealType> mealTypes,
        @Positive Integer targetKcalPerServing,
        MacroStyle macroStyle,
        boolean includeHouseholdRecipes,
        @Min(1) @Max(MAX_LIMIT) Integer limit,
        Long seed) {

    public static final int MAX_INGREDIENTS = 20;
    public static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;

    public SuggestionCriteria toCriteria() {
        return new SuggestionCriteria(mode, ingredientIds, maxTotalTimeMinutes, diet, mealTypes,
                targetKcalPerServing, macroStyle,
                includeHouseholdRecipes,
                limit == null ? DEFAULT_LIMIT : limit,
                seed == null ? Jitter.newSeed() : seed);
    }
}
