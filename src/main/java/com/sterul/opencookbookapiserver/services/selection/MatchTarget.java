package com.sterul.opencookbookapiserver.services.selection;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.Ingredient;

/**
 * One wanted ingredient, widened to the catalogue foods that count as it. A linked selection
 * matches its whole family, so "Forelle" also finds a recipe written with "Forelle gebraten";
 * an unlinked one can only match the very ingredient it came from.
 *
 * @param foodIds empty while the selection is not linked to the catalogue
 */
public record MatchTarget(Long ingredientId, String label, Set<Long> foodIds) {

    public MatchTarget {
        foodIds = Set.copyOf(foodIds);
    }

    public boolean matches(Ingredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        if (ingredientId.equals(ingredient.getId())) {
            return true;
        }
        var food = ingredient.getCatalogueFood();
        return food != null && foodIds.contains(food.getId());
    }
}
