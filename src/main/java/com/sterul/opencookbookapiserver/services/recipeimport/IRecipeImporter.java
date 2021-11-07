package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.entities.Recipe;

public interface IRecipeImporter {
    
    public Recipe importRecipe(String url) throws RecipeImportFailedException;
}
