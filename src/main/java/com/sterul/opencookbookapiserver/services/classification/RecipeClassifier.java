package com.sterul.opencookbookapiserver.services.classification;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * Derives one kind of classification from what a recipe already says. Only reads: writing the
 * value, and remembering that it was derived, is the same for every kind and lives in the runs.
 */
public interface RecipeClassifier<T> {

    ClassifiedAttribute<T> attribute();

    /** What readings are based on, such as a dataset release; recorded on each run. */
    String basis();

    Reading<T> read(Recipe recipe);
}
