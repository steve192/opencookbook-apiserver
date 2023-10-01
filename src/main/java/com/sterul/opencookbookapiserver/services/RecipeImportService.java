package com.sterul.opencookbookapiserver.services;

import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImporterFactory;

@Service
@Transactional
public class RecipeImportService {

    @Autowired
    private RecipeImporterFactory importerFactory;

    @Autowired
    private RecipeService recipeService;

    public Recipe importRecipe(String importUrl, CookpalUser owner)
            throws ImportNotSupportedException, RecipeImportFailedException {
        var importer = importerFactory.getRecipeImporter(importUrl);
        var importedRecipe = importer.importRecipe(importUrl, owner);
        return recipeService.createNewRecipe(importedRecipe);
    }

    public List<String> getAvailableImportHosts() {
        return importerFactory.getAllImporter();
    }

}
