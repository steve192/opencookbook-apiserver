package com.sterul.opencookbookapiserver.controllers.households.responses;

import com.sterul.opencookbookapiserver.controllers.support.DisplayNames;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * A summary rather than the whole recipe: what is not in a listing cannot leak out of it.
 *
 * @param mine whether the viewer owns it, which is also whether they may edit it
 */
public record HouseholdRecipeResponse(Long id, String title, String titleImageUuid,
        String ownerDisplayName, boolean mine) {

    public static HouseholdRecipeResponse of(Recipe recipe, CookpalUser viewer) {
        var images = recipe.getImages();
        return new HouseholdRecipeResponse(recipe.getId(), recipe.getTitle(),
                images.isEmpty() ? null : images.get(0).getUuid(),
                DisplayNames.of(recipe.getOwner()),
                recipe.isOwnedBy(viewer));
    }
}
