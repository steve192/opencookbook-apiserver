package com.sterul.opencookbookapiserver.controllers.sharing.responses;

/** How much of one ingredient a shared recipe needs. */
public record SharedIngredientUseResponse(SharedIngredientResponse ingredient, Float amount, String unit) {
}
