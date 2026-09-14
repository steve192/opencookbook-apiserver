package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.List;
import java.util.Set;

/**
 * A word as the matcher compares it.
 *
 * @param stems    one per language for typed names, whose language is unknown
 * @param parts    compound parts; empty for a simple word
 * @param foodWord whether the word alone names a catalogue food
 */
record AnalyzedWord(String text, Set<String> stems, List<Part> parts, boolean foodWord) {

    AnalyzedWord {
        stems = Set.copyOf(stems);
        parts = List.copyOf(parts);
    }

    /** Unit and state parts of a typed name ("knoblauch-zehe") need no explaining. */
    record Part(String text, Set<String> stems, Kind kind, boolean foodWord) {

        Part {
            stems = Set.copyOf(stems);
        }

        boolean needsExplaining() {
            return kind == Kind.NAME;
        }
    }

    enum Kind {
        NAME, UNIT, STATE
    }

    boolean sharesStemWith(Set<String> otherStems) {
        return stems.stream().anyMatch(otherStems::contains);
    }
}
