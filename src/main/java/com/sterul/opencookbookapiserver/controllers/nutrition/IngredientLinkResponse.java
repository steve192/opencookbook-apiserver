package com.sterul.opencookbookapiserver.controllers.nutrition;

import com.sterul.opencookbookapiserver.entities.Ingredient;

/** @param food null when unlinked or excluded */
public record IngredientLinkResponse(Long ingredientId, String ingredientName, CatalogueFoodResponse food,
        Ingredient.LinkSource linkSource, boolean excluded) {

    public static IngredientLinkResponse of(Ingredient ingredient, String language) {
        var food = ingredient.getCatalogueFood();
        return new IngredientLinkResponse(ingredient.getId(), ingredient.getName(),
                food == null ? null : CatalogueFoodResponse.of(food, language, null), ingredient.getLinkSource(),
                ingredient.isExcludedFromNutrition());
    }
}
