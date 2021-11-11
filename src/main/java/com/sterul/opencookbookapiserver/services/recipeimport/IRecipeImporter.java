package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.entities.account.User;

public interface IRecipeImporter {
    
    public Recipe importRecipe(String url, User owner) throws RecipeImportFailedException;
}
