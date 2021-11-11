package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

public interface IRecipeImporter {
    
    public Recipe importRecipe(String url, User owner) throws RecipeImportFailedException;
}
