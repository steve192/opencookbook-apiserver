package com.sterul.opencookbookapiserver.unit.services.selection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.entities.nutrition.NutritionQuality;
import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;
import com.sterul.opencookbookapiserver.services.selection.NutritionFit;

/** The nutrition judgements suggestion and planning share. */
class NutritionFitTest {

    private static RecipeNutritionSummary perServing(NutritionQuality quality, float kcal, float carbs, float fat,
            float protein) {
        return RecipeNutritionSummary.builder().quality(quality)
                .perServing(NutrientValues.builder().energyKcal(kcal).carbohydrates(carbs).fat(fat).protein(protein).build())
                .build();
    }

    @Test
    void kcalOnTargetFitsFullyAndDoubleTheTargetNotAtAll() {
        assertThat(NutritionFit.kcal(perServing(NutritionQuality.COMPLETE, 500, 0, 0, 0), 500)).contains(1.0);
        assertThat(NutritionFit.kcal(perServing(NutritionQuality.COMPLETE, 1000, 0, 0, 0), 500)).contains(0.0);
    }

    /** A recipe whose nutrition cannot be read is not judged on a guess. */
    @Test
    void unreadableNutritionIsNotJudged() {
        var unavailable = perServing(NutritionQuality.UNAVAILABLE, 500, 50, 10, 20);

        assertThat(NutritionFit.kcal(unavailable, 500)).isEmpty();
        assertThat(NutritionFit.macros(unavailable, MacroStyle.LOW_CARB)).isEmpty();
        assertThat(NutritionFit.kcal(null, 500)).isEmpty();
    }

    @Test
    void lowCarbPrefersFatAndProteinOverCarbohydrates() {
        var steak = perServing(NutritionQuality.COMPLETE, 400, 0, 20, 40);
        var pasta = perServing(NutritionQuality.COMPLETE, 400, 80, 5, 10);

        assertThat(NutritionFit.macros(steak, MacroStyle.LOW_CARB).orElseThrow())
                .isGreaterThan(NutritionFit.macros(pasta, MacroStyle.LOW_CARB).orElseThrow());
    }

    @Test
    void aBalancedStyleAsksForNothing() {
        assertThat(NutritionFit.macros(perServing(NutritionQuality.COMPLETE, 400, 40, 10, 20), MacroStyle.BALANCED))
                .isEmpty();
    }
}
