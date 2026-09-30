package com.sterul.opencookbookapiserver.integration;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;

/** Custom catalogue foods for tests whose ingredients need something to link to. */
final class TestCatalogue {

    private TestCatalogue() {
    }

    /** @param portion the weight of one count unit such as a piece; null for none */
    static CatalogueFood food(CatalogueFoodRepository foods, String key, float kcal, CatalogueFoodPortion portion,
            CatalogueFoodName... names) {
        return foods.save(CatalogueFood.builder()
                .catalogueKey(key)
                .origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(kcal).build())
                .names(new ArrayList<>(List.of(names)))
                .portions(portion == null ? new ArrayList<>() : new ArrayList<>(List.of(portion)))
                .build());
    }

    static CatalogueFoodName name(String language, String name) {
        return CatalogueFoodName.builder().languageIsoCode(language).name(name).display(true)
                .origin(CatalogueFoodName.Origin.ADMIN).build();
    }
}
