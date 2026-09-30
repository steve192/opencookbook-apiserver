package com.sterul.opencookbookapiserver.services;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/** @param householdIds the reader's households whose cookbook shows the recipe */
public record ReadableRecipe(Recipe recipe, Set<String> householdIds) {
}
