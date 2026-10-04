package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.IngredientExtractor;

/** Turns a recipe written as text into an unsaved draft, as a scan is. */
@Component
public class RecipeTextImporter {

    private static final int DEFAULT_SERVINGS = 1;

    private final RecipeTextReader textReader;
    private final IngredientExtractor ingredientExtractor;

    public RecipeTextImporter(RecipeTextReader textReader, IngredientExtractor ingredientExtractor) {
        this.textReader = textReader;
        this.ingredientExtractor = ingredientExtractor;
    }

    /** @throws ApiException IMPORT_NO_RECIPE, carrying the link the text points to where it has one */
    public Recipe importText(String text, CookpalUser owner) {
        var recipe = textReader.read(text);
        if (!recipe.isRecipe()) {
            throw ApiException.withLink(ApiErrorCode.IMPORT_NO_RECIPE, "No recipe in the text", recipe.link());
        }
        return Recipe.builder()
                .owner(owner)
                .title(recipe.title() == null ? "" : recipe.title())
                .servings(recipe.servings() == null ? DEFAULT_SERVINGS : recipe.servings())
                .preparationTime(0L)
                .totalTime(0L)
                .neededIngredients(recipe.ingredientLines().stream()
                        .flatMap(line -> ingredientExtractor.toNeed(line).stream())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .preparationSteps(new ArrayList<>(recipe.steps()))
                .recipeSource(recipe.link())
                .build();
    }
}
