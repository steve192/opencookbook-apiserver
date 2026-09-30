package com.sterul.opencookbookapiserver.controllers.responses;

public record NutritionOfRecipeResponse(Long recipeId, RecipeNutritionResponse nutrition) {
}
