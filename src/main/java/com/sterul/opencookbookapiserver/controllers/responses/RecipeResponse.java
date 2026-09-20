package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.DishRole;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Built field by field, so owners and audit fields don't leak. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeResponse {

    private Long id;
    private String title;

    @Builder.Default
    private List<IngredientNeedResponse> neededIngredients = new ArrayList<>();

    @Builder.Default
    private List<String> preparationSteps = new ArrayList<>();

    @Builder.Default
    private List<RecipeImageResponse> images = new ArrayList<>();

    private int servings;

    private Long preparationTime;
    private Long totalTime;

    private Diet recipeType;

    /** Empty while unknown. */
    @Builder.Default
    private Set<MealType> mealTypes = new HashSet<>();

    /** Null for a dish. */
    private DishRole dishRole;

    private String recipeSource;

    @Builder.Default
    private List<RecipeGroupResponse> recipeGroups = new ArrayList<>();

    /** Absent while nutrition is off, and for unsaved recipes. */
    private NutritionSummaryResponse nutrition;

    public record IngredientNeedResponse(Long id, Float amount, String unit, IngredientSummary ingredient) {

        static IngredientNeedResponse fromEntity(IngredientNeed need) {
            var ingredient = need.getIngredient();
            return new IngredientNeedResponse(need.getId(), need.getAmount(), need.getUnit(),
                    new IngredientSummary(ingredient.getId(), ingredient.getName()));
        }
    }

    /** @param id null for unsaved recipes */
    public record IngredientSummary(Long id, String name) {
    }

    public record RecipeImageResponse(String uuid) {

        static RecipeImageResponse fromEntity(RecipeImage image) {
            return new RecipeImageResponse(image.getUuid());
        }
    }

    public static RecipeResponse fromEntity(Recipe recipe) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .title(recipe.getTitle())
                .neededIngredients(recipe.getNeededIngredients().stream()
                        .map(IngredientNeedResponse::fromEntity)
                        .toList())
                .preparationSteps(recipe.getPreparationSteps())
                .images(recipe.getImages().stream().map(RecipeImageResponse::fromEntity).toList())
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
                .mealTypes(new HashSet<>(recipe.getMealTypes()))
                .dishRole(recipe.getDishRole())
                .recipeSource(recipe.getRecipeSource())
                .build();
    }
}
