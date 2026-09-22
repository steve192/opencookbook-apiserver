package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;

/**
 * What a cook asked for. Everything but the mode is optional; a request answering nothing is a
 * valid "surprise me".
 *
 * @param seed makes a result set reproducible, so asking again is a deliberate new draw
 * @param includeHouseholdRecipes also draw on the household cookbooks the cook may read
 */
public record SuggestionCriteria(
        MatchMode mode,
        List<Long> ingredientIds,
        Long maxTotalTimeMinutes,
        Diet diet,
        Set<MealType> mealTypes,
        Integer targetKcalPerServing,
        MacroStyle macroStyle,
        boolean includeHouseholdRecipes,
        int limit,
        long seed) {

    public SuggestionCriteria {
        mode = mode == null ? MatchMode.ANY_RANKED : mode;
        ingredientIds = ingredientIds == null ? List.of() : List.copyOf(ingredientIds);
        mealTypes = mealTypes == null ? Set.of() : Set.copyOf(mealTypes);
    }

    public boolean hasIngredientFilter() {
        return !ingredientIds.isEmpty();
    }

    public boolean wantsNutrition() {
        return targetKcalPerServing != null || (macroStyle != null && macroStyle != MacroStyle.BALANCED);
    }
}
