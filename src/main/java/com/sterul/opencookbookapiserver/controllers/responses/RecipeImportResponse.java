package com.sterul.opencookbookapiserver.controllers.responses;

/**
 * @param saved true for a recipe website, imported and saved; false for a draft read from a text
 *              or an Instagram post, which has no id until the editor saves it
 */
public record RecipeImportResponse(RecipeResponse recipe, boolean saved) {
}
