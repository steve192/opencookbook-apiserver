package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.util.EnumSet;
import java.util.Set;

/**
 * @param grams      null unless resolved
 * @param ownPortion weighed by the owner's own piece weight
 */
public record AmountInGrams(LineStatus status, Double grams, Set<LineFlag> flags, boolean ownPortion) {

    public AmountInGrams {
        flags = flags.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(flags));
    }

    static AmountInGrams resolved(double grams, Set<LineFlag> flags) {
        return new AmountInGrams(LineStatus.RESOLVED, grams, flags, false);
    }

    static AmountInGrams byOwnPortion(double grams) {
        return new AmountInGrams(LineStatus.RESOLVED, grams, Set.of(), true);
    }

    static AmountInGrams unresolved(LineStatus status) {
        return new AmountInGrams(status, null, Set.of(), false);
    }
}
