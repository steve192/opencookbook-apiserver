package com.sterul.opencookbookapiserver.unit.services.nutrition.calculation;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;

/** Catalogue foods for calculation tests. */
final class Foods {

    private Foods() {
    }

    static CatalogueFood food(String key, float kcalPer100g, CatalogueFoodPortion... portions) {
        return CatalogueFood.builder()
                .catalogueKey(key)
                .origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(kcalPer100g).energyKj(kcalPer100g * 4.184f)
                        .fat(kcalPer100g / 90).protein(kcalPer100g / 40).carbohydrates(kcalPer100g / 40).build())
                .portions(new ArrayList<>(List.of(portions)))
                .build();
    }

    static CatalogueFoodPortion portion(String unitKey, float grams) {
        return CatalogueFoodPortion.builder().unitKey(unitKey).grams(grams).origin(CatalogueFoodPortion.Origin.SOURCE).build();
    }

    static CatalogueFoodPortion estimatedPortion(String unitKey, float grams) {
        return CatalogueFoodPortion.builder().unitKey(unitKey).grams(grams).origin(CatalogueFoodPortion.Origin.ESTIMATED).build();
    }
}
