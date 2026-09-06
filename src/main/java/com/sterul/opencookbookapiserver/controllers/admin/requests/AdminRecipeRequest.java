package com.sterul.opencookbookapiserver.controllers.admin.requests;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminRecipeRequest {

    @NotBlank
    private String title;

    private Integer servings;
    private Long preparationTime;
    private Long totalTime;
    private Recipe.RecipeType recipeType;
    private List<String> preparationSteps;
}
