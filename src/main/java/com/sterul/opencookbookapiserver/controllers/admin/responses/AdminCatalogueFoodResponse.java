package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.controllers.responses.Nutrients;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;

public record AdminCatalogueFoodResponse(
        Long id,
        String catalogueKey,
        CatalogueFood.Origin origin,
        boolean readOnly,
        CatalogueFood.SourceType sourceType,
        String sourceCode,
        String sourceName,
        Long variantOfId,
        String variantOfKey,
        boolean retired,
        boolean negligible,
        Diet dietClass,
        CatalogueFood.DietClassOrigin dietClassOrigin,
        Float densityGPerMl,
        Nutrients nutrients,
        List<Name> names,
        Set<String> states,
        List<Portion> portions,
        long linkedIngredients,
        Instant createdOn,
        Instant lastChange) {

    public record Name(String languageIsoCode, String name, boolean display, CatalogueFoodName.Origin origin) {
    }

    public record Portion(String unitKey, float grams, CatalogueFoodPortion.Origin origin) {
    }

    public static AdminCatalogueFoodResponse fromEntity(CatalogueFood food, long linkedIngredients) {
        var variantOf = food.getVariantOf();
        return new AdminCatalogueFoodResponse(food.getId(), food.getCatalogueKey(), food.getOrigin(), food.isReadOnly(),
                food.getSourceType(), food.getSourceCode(), food.getSourceName(),
                variantOf == null ? null : variantOf.getId(), variantOf == null ? null : variantOf.getCatalogueKey(),
                food.isRetired(), food.isNegligible(), food.getDietClass(), food.getDietClassOrigin(),
                food.getDensityGPerMl(), Nutrients.of(food.getNutrients()),
                food.getNames().stream()
                        .map(name -> new Name(name.getLanguageIsoCode(), name.getName(), name.isDisplay(), name.getOrigin()))
                        .toList(),
                Set.copyOf(food.getStates()),
                food.getPortions().stream()
                        .map(portion -> new Portion(portion.getUnitKey(), portion.getGrams(), portion.getOrigin()))
                        .toList(),
                linkedIngredients, food.getCreatedOn(), food.getLastChange());
    }
}
