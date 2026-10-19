package com.sterul.opencookbookapiserver.controllers.catalogue;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;

/**
 * @param energyKcal    per 100 g
 * @param waterPer100g  grams; null when the source has no value
 * @param starchPer100g grams: available carbohydrate less sugars, which binds water once cooked; null without both
 * @param aisle         null for custom foods nobody placed
 * @param confidence    for search results only
 */
public record CatalogueFoodResponse(Long id, String displayName, String sourceName, CatalogueFood.SourceType sourceType,
        Float energyKcal, Float waterPer100g, Float starchPer100g, Aisle aisle, Double confidence) {

    public static CatalogueFoodResponse of(CatalogueFood food, String language, Double confidence) {
        return new CatalogueFoodResponse(food.getId(), food.displayName(language).orElse(food.getSourceName()),
                food.getSourceName(), food.getSourceType(), food.getNutrients().getEnergyKcal(), food.getWaterGPer100g(),
                starch(food.getNutrients()), food.getAisle(), confidence);
    }

    private static Float starch(NutrientValues nutrients) {
        return nutrients.getCarbohydrates() == null || nutrients.getSugar() == null ? null
                : Math.max(0f, nutrients.getCarbohydrates() - nutrients.getSugar());
    }
}
