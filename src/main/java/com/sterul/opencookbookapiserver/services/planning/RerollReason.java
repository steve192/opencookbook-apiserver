package com.sterul.opencookbookapiserver.services.planning;

/** Why a cook passed over a planned recipe; each steers the replacement away from what put them off. */
public enum RerollReason {
    /** Something else to eat than the same main food again. */
    HAD_RECENTLY,
    /** Less work than the recipe passed over. */
    TOO_MUCH_WORK,
    /** Fewer of the recipe's ingredients, one of which the cook does not have. */
    MISSING_INGREDIENTS,
    /** A dish of its own rather than a sauce or a side, which a recipe not yet marked as one may be. */
    NOT_A_FULL_MEAL
}
