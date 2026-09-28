package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.entities.Ingredient;

/** Links a new ingredient to the catalogue, which lives outside the recipe services. */
public interface IngredientLinker {

    void linkNew(Ingredient ingredient);
}
