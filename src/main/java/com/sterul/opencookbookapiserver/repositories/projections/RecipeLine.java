package com.sterul.opencookbookapiserver.repositories.projections;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;

public record RecipeLine(Long recipeId, String recipeTitle, IngredientNeed need) {
}
