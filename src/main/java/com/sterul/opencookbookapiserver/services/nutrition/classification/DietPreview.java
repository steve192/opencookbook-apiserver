package com.sterul.opencookbookapiserver.services.nutrition.classification;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.IngredientService;

/** The diet a recipe being written would have, read from its ingredients as saving would link them. */
@Component
@ConditionalOnNutritionEnabled
public class DietPreview {

    private final IngredientService ingredientService;
    private final RecipeDietDeriver deriver;

    public DietPreview(IngredientService ingredientService, RecipeDietDeriver deriver) {
        this.ingredientService = ingredientService;
        this.deriver = deriver;
    }

    /** Empty where it cannot be read, just as for a saved recipe. Nothing is stored. */
    public Optional<Diet> of(List<String> ingredientNames, CookpalUser owner) {
        var unsaved = Recipe.builder()
                .neededIngredients(ingredientNames.stream()
                        .map(name -> IngredientNeed.builder()
                                .ingredient(ingredientService.existingOrUnsaved(name, null, owner))
                                .build())
                        .toList())
                .build();
        return deriver.derive(unsaved);
    }
}
