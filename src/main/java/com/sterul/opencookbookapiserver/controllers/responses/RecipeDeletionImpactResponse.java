package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.services.RecipeService;

/**
 * @param households how many households it would leave
 * @param plannedMeals how many planned meals it would remove, in any plan
 */
public record RecipeDeletionImpactResponse(long households, long plannedMeals) {

    public static RecipeDeletionImpactResponse fromResult(RecipeService.DeletionImpact impact) {
        return new RecipeDeletionImpactResponse(impact.households(), impact.plannedMeals());
    }
}
