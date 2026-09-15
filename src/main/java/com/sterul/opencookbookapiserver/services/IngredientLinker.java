package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.entities.Ingredient;

/** Absent while nutrition estimation is turned off. */
public interface IngredientLinker {

    void linkNew(Ingredient ingredient);
}
