package com.sterul.opencookbookapiserver.services;

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

import lombok.extern.slf4j.Slf4j;

/** Copies a recipe, images and all, into another cookbook. Callers settle read access first. */
@Service
@Slf4j
@Transactional
public class RecipeCopier {

    private final RecipeService recipeService;
    private final RecipeImageService recipeImageService;

    public RecipeCopier(RecipeService recipeService, RecipeImageService recipeImageService) {
        this.recipeService = recipeService;
        this.recipeImageService = recipeImageService;
    }

    /**
     * @param source      the recipe to copy
     * @param recipient   who ends up owning the copy
     * @param recipeSource where the copy says it came from
     */
    public Recipe copyTo(Recipe source, CookpalUser recipient, String recipeSource)
            throws IOException {
        log.info("User {} is copying recipe {}", recipient.getUserId(), source.getId());

        var copy = Recipe.builder()
                .owner(recipient)
                .title(source.getTitle())
                .preparationSteps(new ArrayList<>(source.getPreparationSteps()))
                .neededIngredients(copyIngredientNeeds(source.getNeededIngredients()))
                .images(copyImages(source.getImages(), recipient))
                .servings(source.getServings())
                .preparationTime(source.getPreparationTime())
                .totalTime(source.getTotalTime())
                .recipeType(source.getRecipeType())
                .recipeSource(recipeSource)
                // Recipe groups are the original owner's filing, not part of the recipe.
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

    private List<RecipeImage> copyImages(List<RecipeImage> sourceImages, CookpalUser recipient) throws IOException {
        var copies = new ArrayList<RecipeImage>();
        try {
            for (var sourceImage : sourceImages) {
                copies.add(recipeImageService.copyImage(sourceImage.getUuid(), recipient));
            }
        } catch (IOException copyFailed) {
            // A rollback removes the rows but not the files, which nothing would clean up later.
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
                log.error("Could not remove partially copied image {}", copy.getUuid(), cleanupFailed);
            }
        }
    }
}
