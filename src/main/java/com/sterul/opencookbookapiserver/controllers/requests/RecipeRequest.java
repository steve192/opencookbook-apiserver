package com.sterul.opencookbookapiserver.controllers.requests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.DishRole;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;

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
    private List<@Valid IngredientNeedRequest> neededIngredients = new ArrayList<>();

    @Builder.Default
    private List<String> preparationSteps = new ArrayList<>();

    @Builder.Default
    private List<@Valid RecipeImageReference> images = new ArrayList<>();

    private int servings;

    private Long preparationTime;
    private Long totalTime;

    private Diet recipeType;

    /** Empty leaves the recipe's meal unknown, which keeps it eligible for every meal but breakfast. */
    @Builder.Default
    private Set<MealType> mealTypes = new HashSet<>();

    /** Null for a dish. */
    private DishRole dishRole;

    @Builder.Default
    private List<@Valid RecipeGroupRequest> recipeGroups = new ArrayList<>();
}
