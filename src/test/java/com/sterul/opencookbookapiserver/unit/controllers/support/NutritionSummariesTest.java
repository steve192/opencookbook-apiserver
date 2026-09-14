package com.sterul.opencookbookapiserver.unit.controllers.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.controllers.support.NutritionSummaries;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.GramsResolver;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutrition;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;
import com.sterul.opencookbookapiserver.unit.services.nutrition.matching.ShippedCatalogueMatcher;

class NutritionSummariesTest {

    private final NutritionCalculator calculator = ShippedNutritionDataset.calculator();

    @Test
    void aRecipeHasNoSummaryWhileTheCatalogueIsNotReady() {
        var notReady = new CatalogueMatcher(ShippedNutritionDataset.READER);

        assertTrue(new NutritionSummaries(calculator, notReady).of(recipe()).isEmpty());
    }

    @Test
    void aRecipeHasASummaryOnceTheCatalogueIsReady() {
        var summary = new NutritionSummaries(calculator, ShippedCatalogueMatcher.MATCHER).of(recipe());

        assertEquals(RecipeNutrition.Status.UNAVAILABLE, summary.orElseThrow().status());
    }

    private static Recipe recipe() {
        var recipe = new Recipe();
        recipe.setServings(2);
        recipe.setNeededIngredients(new ArrayList<>(List.of()));
        return recipe;
    }
}
