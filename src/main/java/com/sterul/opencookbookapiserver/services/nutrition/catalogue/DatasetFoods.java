package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

import java.util.Collection;
import java.util.HashSet;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/** Maps dataset foods onto catalogue food entities. */
public final class DatasetFoods {

    private DatasetFoods() {
    }

    /** Copies all dataset-owned fields except names and base; unchanged collections cause no writes. */
    public static void copyInto(NutritionDataset.Food food, CatalogueFood entity) {
        entity.setRetired(false);
        entity.setSourceType(CatalogueFood.SourceType.valueOf(food.source().type()));
        entity.setSourceCode(food.source().code());
        entity.setSourceName(food.source().name());
        entity.setNegligible(food.negligible());
        entity.applyDatasetDietClass(dietClass(food));
        entity.setDensityGPerMl(food.densityGPerMl());
        entity.setNutrients(nutrients(food.nutrients()));
        replaceIfChanged(entity.getStates(), new HashSet<>(food.states()));
        replaceIfChanged(entity.getPortions(), food.portions().stream()
                .map(portion -> CatalogueFoodPortion.builder()
                        .unitKey(portion.unit())
                        .grams(portion.grams())
                        .origin(CatalogueFoodPortion.Origin.valueOf(portion.origin()))
                        .build())
                .toList());
    }

    /** Absent only for a dataset built before diet classes; such a food stays unclassified. */
    private static Diet dietClass(NutritionDataset.Food food) {
        return food.dietClass() == null ? null : Diet.valueOf(food.dietClass());
    }

    private static NutrientValues nutrients(NutritionDataset.Nutrients nutrients) {
        return NutrientValues.builder()
                .energyKcal(nutrients.energyKcal())
                .energyKj(nutrients.energyKj())
                .fat(nutrients.fat())
                .saturatedFat(nutrients.saturatedFat())
                .carbohydrates(nutrients.carbohydrates())
                .sugar(nutrients.sugar())
                .fibre(nutrients.fibre())
                .protein(nutrients.protein())
                .salt(nutrients.salt())
                .build();
    }

    private static <T> void replaceIfChanged(Collection<T> current, Collection<T> wanted) {
        if (!new HashSet<>(current).equals(new HashSet<>(wanted)) || current.size() != wanted.size()) {
            current.clear();
            current.addAll(wanted);
        }
    }
}
