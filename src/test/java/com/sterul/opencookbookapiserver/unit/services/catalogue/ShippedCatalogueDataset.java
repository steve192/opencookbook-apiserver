package com.sterul.opencookbookapiserver.unit.services.catalogue;

import com.sterul.opencookbookapiserver.services.nutrition.calculation.GramsResolver;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.SparingUses;

import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.IngredientExtractor;

/** The dataset shipped in the jar, for unit tests that run without Spring. */
public final class ShippedCatalogueDataset {

    public static final CatalogueDatasetReader READER = new CatalogueDatasetReader();
    public static final UnitLexicon UNIT_LEXICON = new UnitLexicon(READER);

    private ShippedCatalogueDataset() {
    }

    public static NutritionCalculator calculator() {
        return new NutritionCalculator(new GramsResolver(UNIT_LEXICON), new SparingUses(READER));
    }

    public static IngredientExtractor ingredientExtractor() {
        return new IngredientExtractor(UNIT_LEXICON);
    }
}
