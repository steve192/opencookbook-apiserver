package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImporterFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeImportService {

    @Autowired
    private RecipeImporterFactory importerFactory;

    public Recipe importRecipe(String importUrl, User owner) throws ImportNotSupportedException, RecipeImportFailedException {
        var importer = importerFactory.getRecipeImporter(importUrl);

        return importer.importRecipe(importUrl, owner);
    }
    
}
