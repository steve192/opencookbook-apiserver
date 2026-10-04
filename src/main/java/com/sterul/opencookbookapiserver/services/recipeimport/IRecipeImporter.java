package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import java.io.IOException;
import java.util.List;

public interface IRecipeImporter {

    Recipe importRecipe(String url, CookpalUser owner);

    List<String> getSupportedHostnames() throws IOException;

    /** True where the import is a draft for the editor rather than a recipe to save as it is. */
    default boolean needsReview() {
        return false;
    }
}
