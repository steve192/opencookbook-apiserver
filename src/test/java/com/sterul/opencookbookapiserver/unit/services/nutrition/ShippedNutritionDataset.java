package com.sterul.opencookbookapiserver.unit.services.nutrition;

import com.sterul.opencookbookapiserver.services.nutrition.calculation.GramsResolver;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.SparingUses;

import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.IngredientExtractor;

/** The dataset shipped in the jar, for unit tests that run without Spring. */
public final class ShippedNutritionDataset {

    public static final NutritionDatasetReader READER = new NutritionDatasetReader();
    public static final UnitLexicon UNIT_LEXICON = new UnitLexicon(READER);

    private ShippedNutritionDataset() {
    }

    public static NutritionCalculator calculator() {
        return new NutritionCalculator(new GramsResolver(UNIT_LEXICON), new SparingUses(READER));
    }

    public static IngredientExtractor ingredientExtractor() {
        return new IngredientExtractor(UNIT_LEXICON);
    }
}
