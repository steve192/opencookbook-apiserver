package com.sterul.opencookbookapiserver.controllers.sharing.requests;

import jakarta.validation.constraints.NotNull;

/** Asks for the public link of a recipe. */
public record ShareRecipeRequest(@NotNull Long recipeId) {
}
