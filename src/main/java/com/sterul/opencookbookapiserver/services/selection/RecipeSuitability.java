package com.sterul.opencookbookapiserver.services.selection;

import java.util.Collections;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/** The hard rules for whether a recipe may be offered at all, the same for a suggestion as for a plan. */
public final class RecipeSuitability {

    private RecipeSuitability() {
    }

    /** An unclassified recipe cannot be offered to somebody who asked for a diet. */
    public static boolean suitsDiet(Recipe recipe, Diet wanted) {
        return wanted == null || wanted.permits(recipe.getRecipeType());
    }

    /**
     * A recipe without meal types stays eligible, so the filter only ever narrows and no recipe is
     * hidden for missing metadata. Breakfast is the exception: offering an untagged dinner there is
     * the most obvious kind of wrong.
     */
    public static boolean suitsMeals(Recipe recipe, Set<MealType> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        if (!recipe.getMealTypes().isEmpty()) {
            return isMarkedFor(recipe, wanted);
        }
        return wanted.stream().anyMatch(type -> type != MealType.BREAKFAST);
    }

    /** Sides and components are never offered as a meal. */
    public static boolean isServedOnItsOwn(Recipe recipe) {
        return recipe.getDishRole() == null;
    }

    /** Whether its owner said the recipe is one of these meals. */
    public static boolean isMarkedFor(Recipe recipe, Set<MealType> wanted) {
        return !Collections.disjoint(recipe.getMealTypes(), wanted);
    }
}
