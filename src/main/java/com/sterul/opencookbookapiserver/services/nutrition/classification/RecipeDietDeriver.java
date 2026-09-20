package com.sterul.opencookbookapiserver.services.nutrition.classification;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * Reads a recipe's diet off the catalogue: the strictest class among its ingredients. One unlinked
 * line leaves the recipe unclassified, since an unlinked "Speck" would make "vegan" a wrong guess.
 */
@Component
public class RecipeDietDeriver {

    /** Empty where the recipe cannot be read, which is not the same as a recipe with no animal in it. */
    public Optional<Diet> derive(Recipe recipe) {
        if (recipe.getNeededIngredients().isEmpty()) {
            return Optional.empty();
        }
        var strictest = Diet.VEGAN;
        var counted = 0;
        for (var need : recipe.getNeededIngredients()) {
            var ingredient = need.getIngredient();
            if (doesNotCount(ingredient)) {
                continue;
            }
            if (isUnreadable(ingredient)) {
                return Optional.empty();
            }
            strictest = strictest.stricterOf(ingredient.getCatalogueFood().getDietClass());
            counted++;
        }
        // Everything excluded means nothing was read; "vegan" would be a claim about no evidence.
        return counted == 0 ? Optional.empty() : Optional.of(strictest);
    }

    /** The first line that cannot be read, for telling a reviewer why a recipe was left alone. */
    public Optional<String> firstUnreadableIngredient(Recipe recipe) {
        return recipe.getNeededIngredients().stream()
                .map(IngredientNeed::getIngredient)
                .filter(ingredient -> !doesNotCount(ingredient) && isUnreadable(ingredient))
                .map(Ingredient::getName)
                .findFirst();
    }

    /** A toothpick its owner excluded from nutrition says nothing about the diet either. */
    private static boolean doesNotCount(Ingredient ingredient) {
        return ingredient == null || ingredient.isExcludedFromNutrition();
    }

    private static boolean isUnreadable(Ingredient ingredient) {
        var food = ingredient.getCatalogueFood();
        return food == null || food.getDietClass() == null;
    }
}
