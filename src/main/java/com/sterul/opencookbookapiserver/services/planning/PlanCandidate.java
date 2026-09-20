package com.sterul.opencookbookapiserver.services.planning;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;

/**
 * A recipe the plan may use, with what planning needs to know about it read once up front.
 *
 * @param nutrition null while nutrition estimation is off
 * @param effort    0 for the least work in this cookbook, 1 for the most; null where nothing is known
 * @param pantryUse how much of each pantry item one cooking of it uses up
 */
public record PlanCandidate(Recipe recipe, RecipeNutritionSummary nutrition, Double effort,
        Map<MatchTarget, Double> pantryUse) {

    public PlanCandidate {
        pantryUse = Map.copyOf(pantryUse);
    }

    public Long id() {
        return recipe.getId();
    }

    /** Fish counts as meat. An unclassified recipe does not count, because it is not known to be meat. */
    public boolean isMeat() {
        return recipe.getRecipeType() == Diet.MEAT;
    }

    public Long mainFoodId() {
        return nutrition == null || nutrition.getMainFood() == null ? null : nutrition.getMainFood().getId();
    }

    /** Somebody said which meals it suits, so it is known to be a dish rather than a sauce nobody marked. */
    public boolean isKnownDish() {
        return !recipe.getMealTypes().isEmpty();
    }

    public boolean sharesMainFood(PlanCandidate other) {
        return mainFoodId() != null && mainFoodId().equals(other.mainFoodId());
    }

    public boolean sharesGroup(PlanCandidate other) {
        return !Collections.disjoint(groupIds(), other.groupIds());
    }

    /** From 0 to 1: how much of what the other recipe needs this one needs too, staples left out. */
    public double shareOfIngredientsOf(PlanCandidate other) {
        var theirs = other.essentialIngredientIds();
        if (theirs.isEmpty()) {
            return 0;
        }
        var shared = essentialIngredientIds().stream().filter(theirs::contains).count();
        return shared / (double) theirs.size();
    }

    private Set<Long> groupIds() {
        return recipe.getRecipeGroups().stream().map(RecipeGroup::getId).collect(Collectors.toSet());
    }

    /** Salt and water are in every kitchen, so they say nothing about what the cook is missing. */
    private Set<Long> essentialIngredientIds() {
        return recipe.getNeededIngredients().stream()
                .map(IngredientNeed::getIngredient)
                .filter(ingredient -> ingredient != null
                        && (ingredient.getCatalogueFood() == null || !ingredient.getCatalogueFood().isNegligible()))
                .map(Ingredient::getId)
                .collect(Collectors.toSet());
    }
}
