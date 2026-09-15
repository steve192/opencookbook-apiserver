package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;

public record AdminFoodReference(Long id, String catalogueKey, String displayNameDe, String displayNameEn, Float energyKcal,
        boolean retired) {

    /** Null for no food. */
    public static AdminFoodReference of(CatalogueFood food) {
        if (food == null) {
            return null;
        }
        return new AdminFoodReference(food.getId(), food.getCatalogueKey(), food.displayName("de").orElse(food.getSourceName()),
                food.displayName("en").orElse(food.getSourceName()), food.getNutrients().getEnergyKcal(), food.isRetired());
    }
}
