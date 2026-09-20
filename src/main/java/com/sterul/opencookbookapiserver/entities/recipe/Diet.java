package com.sterul.opencookbookapiserver.entities.recipe;

/**
 * How strict a diet is, on one scale for recipes and the foods they are made of: a recipe's diet
 * is the strictest of its foods'. Fish counts as meat.
 *
 * Declared from least to most restrictive. The order is the scale, and recipes store it as an ordinal.
 */
public enum Diet {
    VEGAN, VEGETARIAN, MEAT;

    /** Whether a diet of this level allows the given one. An unknown level is never allowed. */
    public boolean permits(Diet other) {
        return other != null && other.ordinal() <= ordinal();
    }

    public Diet stricterOf(Diet other) {
        return permits(other) ? this : other;
    }
}
