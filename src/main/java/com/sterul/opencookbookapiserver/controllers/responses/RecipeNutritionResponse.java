package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineFlag;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineNutrition;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineStatus;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutrition;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/** Line by line. Shared recipes get no ids and no link details. */
public record RecipeNutritionResponse(NutritionSummaryResponse summary, List<Line> lines,
        List<NutritionDataset.Attribution> attributions) {

    /** @param values for the whole recipe */
    public record Line(Long needId, Long ingredientId, String ingredientName, Float amount, String unit, Double grams,
            Nutrients values, Food food, Ingredient.LinkSource linkSource, Float linkConfidence, LineStatus status,
            Set<LineFlag> flags, Boolean ownPortion, boolean warns) {
    }

    public record Food(Long id, String displayName, String sourceName, CatalogueFood.SourceType sourceType) {

        static Food of(CatalogueFood food, String language) {
            return food == null ? null
                    : new Food(food.getId(), food.displayName(language).orElse(food.getSourceName()), food.getSourceName(),
                            food.getSourceType());
        }
    }

    public static RecipeNutritionResponse forOwner(RecipeNutrition nutrition, String language, NutritionDataset.Manifest manifest) {
        return of(nutrition, line -> line(line, language, true), manifest);
    }

    public static RecipeNutritionResponse forShared(RecipeNutrition nutrition, String language, NutritionDataset.Manifest manifest) {
        return of(nutrition, line -> line(line, language, false), manifest);
    }

    private static RecipeNutritionResponse of(RecipeNutrition nutrition, Function<LineNutrition, Line> lines,
            NutritionDataset.Manifest manifest) {
        return new RecipeNutritionResponse(NutritionSummaryResponse.of(nutrition), nutrition.lines().stream().map(lines).toList(),
                manifest.attributions());
    }

    private static Line line(LineNutrition line, String language, boolean forOwner) {
        var need = line.need();
        var ingredient = need.getIngredient();
        return new Line(
                forOwner ? need.getId() : null,
                forOwner ? ingredient.getId() : null,
                ingredient.getName(),
                need.getAmount(),
                need.getUnit(),
                line.grams() == null ? null : Math.round(line.grams() * 10) / 10.0,
                Nutrients.of(line.values()),
                Food.of(ingredient.getCatalogueFood(), language),
                forOwner ? ingredient.getLinkSource() : null,
                forOwner ? ingredient.getLinkConfidence() : null,
                line.status(),
                line.flags(),
                forOwner ? line.ownPortion() : null,
                line.warns());
    }
}
