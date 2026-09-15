package com.sterul.opencookbookapiserver.unit.services.nutrition.calculation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineStatus;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.DatasetFoods;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;
import com.sterul.opencookbookapiserver.unit.services.nutrition.matching.ShippedCatalogueMatcher;

/**
 * Median energy error per serving against nutrition-data/local/gold-recipes.tsv (Chefkoch data, never committed;
 * written by fetch_gold_recipes.py). Release criterion: 20 %; the ceiling is the measured value and only goes down.
 */
class NutritionCalculatorGoldRecipesTest {

    private static final Path GOLD_RECIPES = Path.of("nutrition-data", "local", "gold-recipes.tsv");
    // Measured with matcher version 2 on 40 gold recipes: 0.11. The release criteria ask for 0.20.
    private static final double MEDIAN_ERROR_CEILING = 0.12;
    private static final double MISS_WORTH_EXPLAINING = 0.5;

    private record GoldRecipe(String id, int servings, double kcalPerServing, List<String[]> lines) {
    }

    @Test
    void theMedianErrorPerServingStaysWithinTheCeiling() throws IOException {
        assumeTrue(Files.exists(GOLD_RECIPES), "no gold recipes at " + GOLD_RECIPES);
        var foods = catalogueFoods();
        var calculator = ShippedNutritionDataset.calculator();

        var errors = new ArrayList<Double>();
        var report = new StringBuilder();
        for (var gold : goldRecipes()) {
            var nutrition = calculator.calculate(recipe(gold, foods));
            var estimate = nutrition.values().energyKcal();
            var error = Math.abs(estimate - gold.kcalPerServing()) / gold.kcalPerServing();
            errors.add(error);
            report.append("%s: %.0f kcal estimated, %.0f stated, %.0f %%%n".formatted(gold.id(), estimate, gold.kcalPerServing(), error * 100));
            if (error > MISS_WORTH_EXPLAINING) {
                nutrition.lines().stream()
                        .filter(line -> line.status() != LineStatus.RESOLVED)
                        .forEach(line -> report.append("    %s: %s %s %s%n".formatted(line.status(), line.need().getAmount(),
                                line.need().getUnit(), line.need().getIngredient().getName())));
            }
        }
        errors.sort(Double::compare);
        var median = errors.get(errors.size() / 2);
        System.out.println("Gold recipes, median error " + Math.round(median * 100) + " %\n" + report);

        assertTrue(median <= MEDIAN_ERROR_CEILING, "median error " + median + "\n" + report);
    }

    /** Every food of the shipped dataset as a catalogue food, with its base. */
    private static Map<String, CatalogueFood> catalogueFoods() {
        var shipped = ShippedNutritionDataset.READER.catalogue().foods();
        var foods = new HashMap<String, CatalogueFood>();
        for (var food : shipped) {
            var entity = CatalogueFood.builder().catalogueKey(food.key()).origin(CatalogueFood.Origin.DATASET).build();
            DatasetFoods.copyInto(food, entity);
            foods.put(food.key(), entity);
        }
        shipped.stream().filter(food -> food.variantOf() != null)
                .forEach(food -> foods.get(food.key()).setVariantOf(foods.get(food.variantOf())));
        return foods;
    }

    /** The recipe as cookpal would hold it after saving: every ingredient linked the way the matcher links it. */
    private static Recipe recipe(GoldRecipe gold, Map<String, CatalogueFood> foods) {
        var needs = new ArrayList<IngredientNeed>();
        for (var line : gold.lines()) {
            var ingredient = Ingredient.builder().name(line[2]).build();
            ShippedCatalogueMatcher.MATCHER.match(line[2], "de").ifPresent(candidate -> ingredient.linkAutomatically(
                    foods.get(candidate.foodKey()), (float) candidate.confidence(), CatalogueMatcher.VERSION, Instant.EPOCH, null));
            needs.add(IngredientNeed.builder()
                    .amount(line[0].isEmpty() ? null : Float.parseFloat(line[0]))
                    .unit(line[1])
                    .ingredient(ingredient)
                    .build());
        }
        var recipe = new Recipe();
        recipe.setServings(gold.servings());
        recipe.setNeededIngredients(needs);
        return recipe;
    }

    private static List<GoldRecipe> goldRecipes() throws IOException {
        var recipes = new LinkedHashMap<String, GoldRecipe>();
        for (var row : Files.readAllLines(GOLD_RECIPES)) {
            if (row.isBlank() || row.startsWith("#")) {
                continue;
            }
            var columns = row.split("\t", -1);
            if (columns[0].equals("recipe")) {
                recipes.put(columns[1], new GoldRecipe(columns[1], Integer.parseInt(columns[2]), Double.parseDouble(columns[3]), new ArrayList<>()));
            } else {
                recipes.get(columns[1]).lines().add(new String[] {columns[2], columns[3], columns[4]});
            }
        }
        return List.copyOf(recipes.values());
    }
}
