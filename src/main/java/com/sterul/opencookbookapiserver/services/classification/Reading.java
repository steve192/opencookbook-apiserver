package com.sterul.opencookbookapiserver.services.classification;

import java.util.Optional;

/**
 * What a classifier made of one recipe, with the reason in the words a reviewer can check it by:
 * the ingredient that decided the value, or the one that kept the recipe from being read.
 * A bare "this is MEAT" cannot be reviewed.
 *
 * @param value empty where the recipe cannot be read, which is recorded rather than guessed at
 */
public record Reading<T>(Optional<T> value, String reason) {

    public static <T> Reading<T> of(T value, String reason) {
        return new Reading<>(Optional.of(value), reason);
    }

    public static <T> Reading<T> unreadable(String reason) {
        return new Reading<>(Optional.empty(), reason);
    }
}
