package com.sterul.opencookbookapiserver.controllers.dto;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe.RecipeType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class RecipeBaseDTO {
    private Long id;
    private String title;

    @Builder.Default
    private List<IngredientNeed> neededIngredients = new ArrayList<>();
    @Builder.Default
    private List<String> preparationSteps = new ArrayList<>();

    @Builder.Default
    private List<RecipeImage> images = new ArrayList<>();
    private int servings;

    private Long preparationTime;
    private Long totalTime;

    private RecipeType recipeType;
}
