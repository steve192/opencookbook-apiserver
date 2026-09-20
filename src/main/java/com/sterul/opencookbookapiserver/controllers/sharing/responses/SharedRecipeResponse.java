package com.sterul.opencookbookapiserver.controllers.sharing.responses;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.controllers.responses.NutritionSummaryResponse;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * A recipe as it is published to anybody holding a share link.
 *
 * Purposely not a reuse of the response the owner gets.
 *
 * @param nutrition null while nutrition is off
 */
public record SharedRecipeResponse(
        String title,
        List<SharedIngredientUseResponse> neededIngredients,
        List<String> preparationSteps,
        List<SharedImageResponse> images,
        int servings,
        Long preparationTime,
        Long totalTime,
        Diet recipeType,
        String recipeSource,
        NutritionSummaryResponse nutrition) {

    public static SharedRecipeResponse fromEntity(Recipe recipe, NutritionSummaryResponse nutrition) {
        return new SharedRecipeResponse(
                recipe.getTitle(),
                recipe.getNeededIngredients().stream()
                        .map(need -> new SharedIngredientUseResponse(
                                new SharedIngredientResponse(need.getIngredient().getName()),
                                need.getAmount(),
                                need.getUnit()))
                        .toList(),
                // Copied rather than shared so that nothing can reach back into the entity, and
                // copied permissively: a step that somehow ended up null must not turn the one
                // endpoint anybody can reach into a server error.
                new ArrayList<>(recipe.getPreparationSteps()),
                recipe.getImages().stream()
                        .map(image -> new SharedImageResponse(image.getUuid()))
                        .toList(),
                recipe.getServings(),
                recipe.getPreparationTime(),
                recipe.getTotalTime(),
                recipe.getRecipeType(),
                recipe.getRecipeSource(),
                nutrition);
    }
}
