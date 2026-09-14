package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/** Single-word unit ("Zehe") and state ("getrocknet") words of the lexicons. */
final class LexiconWords {

    private final Set<String> unitWords = new HashSet<>();
    private final Set<String> stateWords = new HashSet<>();
    private final Map<String, String> statesByStem = new HashMap<>();

    LexiconWords(NutritionDataset.Lexicons lexicons, TextAnalysis analysis) {
        for (var lexicon : lexicons.lexicons()) {
            lexicon.units().values().forEach(words -> words.forEach(word -> singleWord(word, analysis)
                    .ifPresent(unitWords::add)));
            lexicon.states().forEach((state, words) -> words.forEach(word -> singleWord(word, analysis)
                    .ifPresent(single -> {
                        stateWords.add(single);
                        analysis.stems(single, lexicon.language()).forEach(stem -> statesByStem.put(stem, state));
                    })));
        }
    }

    boolean isUnit(String word) {
        return unitWords.contains(word);
    }

    Optional<String> state(String word, TextAnalysis analysis) {
        return analysis.stems(word).stream().map(statesByStem::get).filter(Objects::nonNull).findFirst();
    }

    /** For splitting compounds like "knoblauchzehe". */
    Set<String> words() {
        var words = new HashSet<>(unitWords);
        words.addAll(stateWords);
        return words;
    }

    private static Optional<String> singleWord(String entry, TextAnalysis analysis) {
        var words = analysis.words(entry);
        return words.size() == 1 ? Optional.of(words.get(0)) : Optional.empty();
    }
}
