package com.sterul.opencookbookapiserver.controllers.requests;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe.RecipeType;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** References are resolved against what the caller owns; id and source are the server's. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeRequest {

    private String title;

    @Builder.Default
    @Valid
    private List<IngredientNeedRequest> neededIngredients = new ArrayList<>();

    @Builder.Default
    private List<String> preparationSteps = new ArrayList<>();

    @Builder.Default
    @Valid
    private List<RecipeImageReference> images = new ArrayList<>();

    private int servings;

    private Long preparationTime;
    private Long totalTime;

    private RecipeType recipeType;

    @Builder.Default
    @Valid
    private List<RecipeGroupRequest> recipeGroups = new ArrayList<>();
}
