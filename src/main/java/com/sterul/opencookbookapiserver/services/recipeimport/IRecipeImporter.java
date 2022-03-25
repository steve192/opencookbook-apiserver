package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import java.io.IOException;
import java.util.List;

public interface IRecipeImporter {

    Recipe importRecipe(String url, User owner) throws RecipeImportFailedException, ImportNotSupportedException;

    List<String> getSupportedHostnames() throws IOException;
}
