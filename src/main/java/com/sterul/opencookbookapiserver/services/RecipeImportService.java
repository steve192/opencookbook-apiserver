package com.sterul.opencookbookapiserver.services;

import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImporterFactory;

@Service
@Transactional
public class RecipeImportService {

    private final RecipeImporterFactory importerFactory;
    private final RecipeService recipeService;

    public RecipeImportService(RecipeImporterFactory importerFactory, RecipeService recipeService) {
        this.importerFactory = importerFactory;
        this.recipeService = recipeService;
    }

    public Recipe importRecipe(String importUrl, CookpalUser owner) {
        var importer = importerFactory.getRecipeImporter(importUrl);
        var importedRecipe = importer.importRecipe(importUrl, owner);
        return recipeService.createNewRecipe(importedRecipe);
    }

    public List<String> getAvailableImportHosts() {
        return importerFactory.getAllImporter();
    }

}
