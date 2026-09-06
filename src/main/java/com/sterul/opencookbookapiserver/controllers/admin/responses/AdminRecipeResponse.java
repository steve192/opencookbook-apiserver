package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;

public record AdminRecipeResponse(
        Long id,
        String title,
        Long ownerUserId,
        String ownerEmailAddress,
        int servings,
        Long preparationTime,
        Long totalTime,
        Recipe.RecipeType recipeType,
        String recipeSource,
        int ingredientCount,
        int stepCount,
        List<String> ingredients,
        List<String> preparationSteps,
        List<String> imageUuids,
        List<String> recipeGroups,
        Instant createdOn,
        Instant lastChange) {

    public static AdminRecipeResponse fromEntity(Recipe recipe) {
        var owner = recipe.getOwner();
        return new AdminRecipeResponse(
                recipe.getId(),
                recipe.getTitle(),
                owner == null ? null : owner.getUserId(),
                owner == null ? null : owner.getEmailAddress(),
                recipe.getServings(),
                recipe.getPreparationTime(),
                recipe.getTotalTime(),
                recipe.getRecipeType(),
                recipe.getRecipeSource(),
                recipe.getNeededIngredients().size(),
                recipe.getPreparationSteps().size(),
                recipe.getNeededIngredients().stream().map(IngredientNeed::describe).toList(),
                List.copyOf(recipe.getPreparationSteps()),
                recipe.getImages().stream().map(RecipeImage::getUuid).toList(),
                recipe.getRecipeGroups().stream().map(RecipeGroup::getTitle).toList(),
                recipe.getCreatedOn(),
                recipe.getLastChange());
    }

}
