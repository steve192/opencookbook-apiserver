package com.sterul.opencookbookapiserver.services.planning;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sterul.opencookbookapiserver.services.selection.MatchTarget;

/**
 * What the cook has and wants used up, as amounts the plan spends: grams where the item can be
 * weighed, otherwise a single use.
 *
 * @param initial  per item, grams where it is measured, otherwise 1 use
 * @param measured the items counted in grams
 */
public record PantryBudget(Map<MatchTarget, Double> initial, Set<MatchTarget> measured) {

    public static final PantryBudget EMPTY = new PantryBudget(Map.of(), Set.of());

    public PantryBudget {
        initial = Map.copyOf(initial);
        measured = Set.copyOf(measured);
    }

    public Set<MatchTarget> items() {
        return initial.keySet();
    }

    public boolean isEmpty() {
        return initial.isEmpty();
    }

    public Map<MatchTarget, Double> fresh() {
        return new HashMap<>(initial);
    }
}
