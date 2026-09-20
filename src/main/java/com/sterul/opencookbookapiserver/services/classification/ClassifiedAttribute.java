package com.sterul.opencookbookapiserver.services.classification;

import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/**
 * Where one kind of classification lives on a recipe, and how its value is written down in a run.
 * Adding a kind is adding one of these and a {@link RecipeClassifier}; runs and provenance stay as they are.
 */
public interface ClassifiedAttribute<T> {

    ClassificationKind kind();

    /** Null while the recipe has none. */
    T valueOf(Recipe recipe);

    void write(Recipe recipe, T value);

    String encode(T value);

    T decode(String encoded);

    default String encodedValueOf(Recipe recipe) {
        var value = valueOf(recipe);
        return value == null ? null : encode(value);
    }

    default void writeEncoded(Recipe recipe, String encoded) {
        write(recipe, encoded == null ? null : decode(encoded));
    }
}
