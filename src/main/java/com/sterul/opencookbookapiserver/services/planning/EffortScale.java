package com.sterul.opencookbookapiserver.services.planning;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * How much work a recipe is, from its ingredients, steps and time. Ranked within the cook's own
 * cookbook, because "elaborate" means something else to a cook whose longest recipe takes 30 minutes.
 */
public final class EffortScale {

    /** A step is more work than an ingredient; a quarter of an hour counts like one step. */
    private static final double PER_INGREDIENT = 1.0;
    private static final double PER_STEP = 1.5;
    private static final double PER_QUARTER_HOUR = 1.5;

    private EffortScale() {
    }

    /** Each recipe's rank in the cookbook, from 0 (least work) to 1 (most); absent for a recipe with nothing recorded. */
    public static Map<Long, Double> rank(List<Recipe> recipes) {
        var raw = new HashMap<Long, Double>();
        recipes.forEach(recipe -> {
            var work = rawWork(recipe);
            if (work > 0) {
                raw.put(recipe.getId(), work);
            }
        });
        var ordered = raw.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.naturalOrder())).toList();
        var ranks = new HashMap<Long, Double>();
        for (var position = 0; position < ordered.size(); position++) {
            ranks.put(ordered.get(position).getKey(), ordered.size() == 1 ? 0.5 : position / (double) (ordered.size() - 1));
        }
        return ranks;
    }

    static double rawWork(Recipe recipe) {
        var minutes = recipe.minutesNeeded();
        return recipe.getNeededIngredients().size() * PER_INGREDIENT
                + recipe.getPreparationSteps().size() * PER_STEP
                + (minutes == null ? 0 : minutes / 15.0 * PER_QUARTER_HOUR);
    }
}
