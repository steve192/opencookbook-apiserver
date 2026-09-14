package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;

/**
 * @param grams null unless resolved
 * @param warns whether the line may change the recipe's values noticeably
 */
public record LineNutrition(IngredientNeed need, LineStatus status, Double grams, NutrientAmounts values, Set<LineFlag> flags,
        boolean ownPortion, boolean warns) {

    public LineNutrition {
        flags = Set.copyOf(flags);
    }
}
