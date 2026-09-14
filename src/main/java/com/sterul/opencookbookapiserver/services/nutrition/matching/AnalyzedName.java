package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.List;
import java.util.Set;

/**
 * @param qualifiers numbers and bracketed words of a catalogue name: they may explain typed words but need not be
 *                   typed; empty for typed names
 */
record AnalyzedName(String name, List<AnalyzedWord> words, List<AnalyzedWord> qualifiers, Set<String> states) {

    AnalyzedName {
        words = List.copyOf(words);
        qualifiers = List.copyOf(qualifiers);
        states = Set.copyOf(states);
    }
}
