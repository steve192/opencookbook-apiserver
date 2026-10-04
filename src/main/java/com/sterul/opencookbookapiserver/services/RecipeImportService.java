package com.sterul.opencookbookapiserver.services;

import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.recipeimport.GoogleShareLinkResolver;
import com.sterul.opencookbookapiserver.services.recipeimport.Links;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImporterFactory;
import com.sterul.opencookbookapiserver.services.recipeimport.text.RecipeTextImporter;

@Service
@Transactional
public class RecipeImportService {

    private final GoogleShareLinkResolver shareLinks;
    private final RecipeImporterFactory importerFactory;
    private final RecipeTextImporter textImporter;
    private final RecipeService recipeService;

    public RecipeImportService(GoogleShareLinkResolver shareLinks, RecipeImporterFactory importerFactory,
            RecipeTextImporter textImporter, RecipeService recipeService) {
        this.shareLinks = shareLinks;
        this.importerFactory = importerFactory;
        this.textImporter = textImporter;
        this.recipeService = recipeService;
    }

    /** @param saved false for a draft, which has no id until the editor saves it */
    public record ImportedRecipe(Recipe recipe, boolean saved) {
    }

    /** @param input a link, with at most a title beside it, or a recipe as text */
    public ImportedRecipe importRecipe(String input, CookpalUser owner) {
        return Links.shared(input)
                .map(link -> importLink(link, owner))
                .orElseGet(() -> new ImportedRecipe(textImporter.importText(input, owner), false));
    }

    public List<String> getAvailableImportHosts() {
        return importerFactory.getAllImporter();
    }

    private ImportedRecipe importLink(String link, CookpalUser owner) {
        var url = shareLinks.resolve(link);
        var importer = importerFactory.getRecipeImporter(url);
        var recipe = importer.importRecipe(url, owner);
        return importer.needsReview()
                ? new ImportedRecipe(recipe, false)
                : new ImportedRecipe(recipeService.createNewRecipe(recipe), true);
    }
}
