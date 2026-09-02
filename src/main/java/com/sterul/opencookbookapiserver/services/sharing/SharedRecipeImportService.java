package com.sterul.opencookbookapiserver.services.sharing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/**
 * Copies a shared recipe into somebody else's cookbook.
 */
@Service
@Slf4j
@Transactional
public class SharedRecipeImportService {

    private final ShareService shareService;
    private final RecipeService recipeService;
    private final RecipeImageService recipeImageService;
    private final ShareLinkFactory shareLinkFactory;

    public SharedRecipeImportService(ShareService shareService, RecipeService recipeService,
            RecipeImageService recipeImageService, ShareLinkFactory shareLinkFactory) {
        this.shareService = shareService;
        this.recipeService = recipeService;
        this.recipeImageService = recipeImageService;
        this.shareLinkFactory = shareLinkFactory;
    }

    public Recipe importSharedRecipe(String shareId, CookpalUser importer) throws ElementNotFound, IOException {
        var sharedRecipe = shareService.resolveSharedRecipe(shareId);
        log.info("User {} is importing shared recipe {}", importer.getUserId(), sharedRecipe.getId());

        var copy = Recipe.builder()
                .owner(importer)
                .title(sharedRecipe.getTitle())
                .preparationSteps(new ArrayList<>(sharedRecipe.getPreparationSteps()))
                .neededIngredients(copyIngredientNeeds(sharedRecipe.getNeededIngredients()))
                .images(copyImages(sharedRecipe.getImages(), importer))
                .servings(sharedRecipe.getServings())
                .preparationTime(sharedRecipe.getPreparationTime())
                .totalTime(sharedRecipe.getTotalTime())
                .recipeType(sharedRecipe.getRecipeType())
                // Where the copy came from, so the origin stays traceable after the link lapses.
                .recipeSource(shareLinkFactory.linkTo(shareId))
                // Recipe groups are how the sharer organises their own cookbook, not part of the
                // recipe, so they are deliberately not carried over.
                .recipeGroups(new ArrayList<>())
                .build();

        return recipeService.createNewRecipe(copy);
    }

    private List<IngredientNeed> copyIngredientNeeds(List<IngredientNeed> sourceNeeds) {
        return sourceNeeds.stream()
                .map(sourceNeed -> IngredientNeed.builder()
                        .amount(sourceNeed.getAmount())
                        .unit(sourceNeed.getUnit())
                        .ingredient(Ingredient.builder()
                                .name(sourceNeed.getIngredient().getName())
                                .build())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<RecipeImage> copyImages(List<RecipeImage> sourceImages, CookpalUser importer) throws IOException {
        var copies = new ArrayList<RecipeImage>();
        try {
            for (var sourceImage : sourceImages) {
                copies.add(recipeImageService.copyImage(sourceImage.getUuid(), importer));
            }
        } catch (IOException copyFailed) {
            // Rolling back takes the rows away but not the files, and nothing ever looks for a
            // file whose row does not exist. So a half finished import would leave them on disk
            // for good.
            discardCopies(copies);
            throw copyFailed;
        }
        return copies;
    }

    private void discardCopies(List<RecipeImage> copies) {
        for (var copy : copies) {
            try {
                recipeImageService.deleteImage(copy.getUuid());
            } catch (IOException cleanupFailed) {
                log.error("Could not remove partially imported image {}", copy.getUuid(), cleanupFailed);
            }
        }
    }
}
