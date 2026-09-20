package com.sterul.opencookbookapiserver.services.planning;

/**
 * Something else asked for one slot.
 *
 * @param reason null where the cook just wants something else
 */
public record Reroll(Long rejectedRecipeId, RerollReason reason) {
}
