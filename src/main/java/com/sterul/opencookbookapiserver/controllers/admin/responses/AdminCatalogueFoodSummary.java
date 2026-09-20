package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;

public record AdminCatalogueFoodSummary(
        Long id,
        String catalogueKey,
        CatalogueFood.Origin origin,
        CatalogueFood.SourceType sourceType,
        String sourceName,
        String displayNameDe,
        String displayNameEn,
        String variantOfKey,
        Float energyKcal,
        Diet dietClass,
        CatalogueFood.DietClassOrigin dietClassOrigin,
        boolean retired,
        int nameCount,
        int portionCount) {

    public static AdminCatalogueFoodSummary fromEntity(CatalogueFood food) {
        var variantOf = food.getVariantOf();
        return new AdminCatalogueFoodSummary(food.getId(), food.getCatalogueKey(), food.getOrigin(), food.getSourceType(),
                food.getSourceName(), food.displayName("de").orElse(null), food.displayName("en").orElse(null),
                variantOf == null ? null : variantOf.getCatalogueKey(), food.getNutrients().getEnergyKcal(),
                food.getDietClass(), food.getDietClassOrigin(), food.isRetired(),
                food.getNames().size(), food.getPortions().size());
    }
}
