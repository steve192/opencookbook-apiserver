package com.sterul.opencookbookapiserver.services.nutrition.classification;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.classification.ClassifiedAttribute;
import com.sterul.opencookbookapiserver.services.classification.DietAttribute;
import com.sterul.opencookbookapiserver.services.classification.Reading;
import com.sterul.opencookbookapiserver.services.classification.RecipeClassifier;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

/** Reads recipe diets off the catalogue. */
@Component
@ConditionalOnNutritionEnabled
public class DietClassifier implements RecipeClassifier<Diet> {

    private final DietAttribute attribute;
    private final RecipeDietDeriver deriver;
    private final NutritionDatasetReader dataset;

    public DietClassifier(DietAttribute attribute, RecipeDietDeriver deriver, NutritionDatasetReader dataset) {
        this.attribute = attribute;
        this.deriver = deriver;
        this.dataset = dataset;
    }

    @Override
    public ClassifiedAttribute<Diet> attribute() {
        return attribute;
    }

    /** The dataset release the food classes came from, so a later release explains a different outcome. */
    @Override
    public String basis() {
        return dataset.manifest().label();
    }

    @Override
    public Reading<Diet> read(Recipe recipe) {
        return deriver.derive(recipe)
                .map(diet -> Reading.of(diet, reasonFor(recipe, diet)))
                .orElseGet(() -> Reading.unreadable(unreadableReason(recipe)));
    }

    /** Names the ingredient that stopped the reading, which is what a reviewer needs to fix it. */
    private String unreadableReason(Recipe recipe) {
        return deriver.firstUnreadableIngredient(recipe)
                .map(name -> "not linked to the catalogue: " + name)
                .orElse("no ingredients that count towards a diet");
    }

    /** Names the ingredient the class comes from; for a vegan recipe there is no single one. */
    private static String reasonFor(Recipe recipe, Diet derived) {
        return strictestIngredientName(recipe, derived)
                .map(name -> derived + " because of " + name)
                .orElse("every ingredient is linked and plant-based");
    }

    private static Optional<String> strictestIngredientName(Recipe recipe, Diet derived) {
        if (derived == Diet.VEGAN) {
            return Optional.empty();
        }
        return recipe.getNeededIngredients().stream()
                .map(IngredientNeed::getIngredient)
                .filter(ingredient -> ingredient != null && ingredient.getCatalogueFood() != null
                        && derived == ingredient.getCatalogueFood().getDietClass())
                .map(Ingredient::getName)
                .findFirst();
    }
}
