package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.controllers.dto.RecipeBaseDTO;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class RecipeResponse extends RecipeBaseDTO {

    @Builder.Default
    private List<RecipeGroupResponse> recipeGroups = new ArrayList<>();

    public static RecipeResponse fromEntity(Recipe recipe) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .title(recipe.getTitle())
                .images(recipe.getImages())
                .neededIngredients(recipe.getNeededIngredients())
                .preparationSteps(recipe.getPreparationSteps())
                .recipeGroups(recipe.getRecipeGroups().stream()
                        .map(recipeGroup -> RecipeGroupResponse.builder()
                                .title(recipeGroup.getTitle())
                                .id(recipeGroup.getId())
                                .build())
                        .toList())
                .servings(recipe.getServings())
                .preparationTime(recipe.getPreparationTime())
                .totalTime(recipe.getTotalTime())
                .recipeType(recipe.getRecipeType())
                .recipeSource(recipe.getRecipeSource())
                .build();
    }
}
