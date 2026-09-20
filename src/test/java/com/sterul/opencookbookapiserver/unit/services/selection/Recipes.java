package com.sterul.opencookbookapiserver.unit.services.selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/** Cookbooks for the suggestion tests. */
public final class Recipes {

    private Recipes() {
    }

    public static Recipe recipe(long id, String title, Ingredient... ingredients) {
        return Recipe.builder()
                .id(id)
                .title(title)
                .neededIngredients(Arrays.stream(ingredients)
                        .map(ingredient -> IngredientNeed.builder().ingredient(ingredient).build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }

    /** A line naming no ingredient at all, as an imported recipe can leave behind. */
    public static Recipe withNamelessLine(Recipe recipe) {
        recipe.getNeededIngredients().add(IngredientNeed.builder().build());
        return recipe;
    }

    public static Ingredient ingredient(long id, String name) {
        return Ingredient.builder().id(id).name(name).build();
    }

    public static Ingredient linked(long id, String name, CatalogueFood food) {
        return Ingredient.builder().id(id).name(name).catalogueFood(food).build();
    }

    public static CatalogueFood food(long id, String catalogueKey) {
        return CatalogueFood.builder().id(id).catalogueKey(catalogueKey).build();
    }

    public static CatalogueFood variantOf(long id, String catalogueKey, CatalogueFood base) {
        return CatalogueFood.builder().id(id).catalogueKey(catalogueKey).variantOf(base).build();
    }
}
