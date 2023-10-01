package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.util.List;

import com.sterul.opencookbookapiserver.repositories.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.entities.recipe.Recipe;

public interface IRecipeImporter {

    Recipe importRecipe(String url, User owner) throws RecipeImportFailedException, ImportNotSupportedException;

    List<String> getSupportedHostnames() throws IOException;
}
