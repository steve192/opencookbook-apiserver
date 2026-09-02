package com.sterul.opencookbookapiserver.controllers.sharing.responses;

/**
 * An ingredient of a shared recipe: the name only. An ingredient row belongs to whoever created
 * it, and none of the rest of it. Id, owner, nutrients a link recipient business.
 */
public record SharedIngredientResponse(String name) {
}
