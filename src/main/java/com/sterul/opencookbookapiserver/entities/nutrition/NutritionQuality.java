package com.sterul.opencookbookapiserver.entities.nutrition;

/** How much of a recipe's nutrition could be read, as stored; separate so entities do not depend on the calculator. */
public enum NutritionQuality {
    COMPLETE,
    /** Shown with a warning. */
    INCOMPLETE,
    /** Too uncertain to show, and too uncertain to rank on. */
    UNAVAILABLE
}
