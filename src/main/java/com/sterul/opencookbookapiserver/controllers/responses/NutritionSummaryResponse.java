package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutrition;

/** @param status UNAVAILABLE: do not show the values */
public record NutritionSummaryResponse(RecipeNutrition.Basis basis, RecipeNutrition.Status status, Nutrients values,
        int warningCount) {

    public static NutritionSummaryResponse of(RecipeNutrition nutrition) {
        return new NutritionSummaryResponse(nutrition.basis(), nutrition.status(), Nutrients.of(nutrition.values()),
                nutrition.warningCount());
    }
}
