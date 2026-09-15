package com.sterul.opencookbookapiserver.controllers.nutrition;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;

/**
 * @param energyKcal per 100 g
 * @param confidence for search results only
 */
public record CatalogueFoodResponse(Long id, String displayName, String sourceName, CatalogueFood.SourceType sourceType,
        Float energyKcal, Double confidence) {

    public static CatalogueFoodResponse of(CatalogueFood food, String language, Double confidence) {
        return new CatalogueFoodResponse(food.getId(), food.displayName(language).orElse(food.getSourceName()),
                food.getSourceName(), food.getSourceType(), food.getNutrients().getEnergyKcal(), confidence);
    }
}
