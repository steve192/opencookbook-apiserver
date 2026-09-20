package com.sterul.opencookbookapiserver.services;

/** A recipe's content changed, so anything derived from it is stale. */
public record RecipeChangedEvent(Long recipeId) {
}
