package com.sterul.opencookbookapiserver.services.selection;

import java.util.Optional;

import com.sterul.opencookbookapiserver.entities.nutrition.NutritionQuality;
import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;

/**
 * How well a recipe's nutrition per serving suits what a cook asked for, from 0 to 1. Empty where
 * it cannot be judged: a recipe whose nutrition cannot be read is not judged on a guess.
 */
public final class NutritionFit {

    // Atwater factors: the energy a gram of each macronutrient carries.
    private static final double KCAL_PER_GRAM_CARBOHYDRATES = 4;
    private static final double KCAL_PER_GRAM_FAT = 9;
    private static final double KCAL_PER_GRAM_PROTEIN = 4;

    /** A protein share this high is as good as a recipe gets. */
    private static final double RICH_IN_PROTEIN_SHARE = 0.5;

    private NutritionFit() {
    }

    /** Judged relative to the target, so being 100 kcal out matters more for a snack than for a dinner. */
    public static Optional<Double> kcal(RecipeNutritionSummary nutrition, double target) {
        if (!isReadable(nutrition) || target <= 0 || nutrition.getPerServing().getEnergyKcal() == null) {
            return Optional.empty();
        }
        var missBy = Math.min(1, Math.abs(nutrition.getPerServing().getEnergyKcal() - target) / target);
        return Optional.of(1 - missBy);
    }

    /** How the energy is split between the macronutrients; a balanced style asks for nothing. */
    public static Optional<Double> macros(RecipeNutritionSummary nutrition, MacroStyle style) {
        if (!isReadable(nutrition) || style == null) {
            return Optional.empty();
        }
        var values = nutrition.getPerServing();
        var carbohydrates = orZero(values.getCarbohydrates()) * KCAL_PER_GRAM_CARBOHYDRATES;
        var fat = orZero(values.getFat()) * KCAL_PER_GRAM_FAT;
        var protein = orZero(values.getProtein()) * KCAL_PER_GRAM_PROTEIN;
        var total = carbohydrates + fat + protein;
        if (total <= 0) {
            return Optional.empty();
        }
        return switch (style) {
            case LOW_CARB -> Optional.of(1 - carbohydrates / total);
            case LOW_FAT -> Optional.of(1 - fat / total);
            case HIGH_PROTEIN -> Optional.of(Math.min(1, protein / total / RICH_IN_PROTEIN_SHARE));
            case BALANCED -> Optional.empty();
        };
    }

    private static boolean isReadable(RecipeNutritionSummary nutrition) {
        return nutrition != null && nutrition.getQuality() != NutritionQuality.UNAVAILABLE;
    }

    private static double orZero(Float grams) {
        return grams == null ? 0 : grams;
    }
}
