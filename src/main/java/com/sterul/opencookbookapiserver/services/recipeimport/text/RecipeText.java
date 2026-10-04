package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.List;

/** @param link the first link: the recipe's source, or where a text without one points to it */
public record RecipeText(String title, Integer servings, List<String> ingredientLines, List<String> steps,
        String link) {

    static RecipeText none(String link) {
        return new RecipeText(null, null, List.of(), List.of(), link);
    }

    public boolean isRecipe() {
        return !ingredientLines.isEmpty();
    }
}
