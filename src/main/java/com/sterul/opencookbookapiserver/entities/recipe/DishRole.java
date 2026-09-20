package com.sterul.opencookbookapiserver.entities.recipe;

/**
 * What a recipe is when it is not a dish of its own. A recipe without one is a dish, served on its own;
 * the meals it suits say which.
 */
public enum DishRole {
    /** Served alongside a dish: rice, a side salad, bread. */
    SIDE,
    /** Goes into or onto another dish: a sauce, a dip, a dressing, a dough, a stock. */
    COMPONENT
}
