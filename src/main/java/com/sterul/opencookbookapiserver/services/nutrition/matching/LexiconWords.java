package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/** Single-word unit ("Zehe"), state ("getrocknet") and description ("gehackt") words of the lexicons. */
final class LexiconWords {

    private final TextAnalysis analysis;
    private final Set<String> unitWords = new HashSet<>();
    private final Set<String> stateWords = new HashSet<>();
    private final Map<String, String> statesByStem = new HashMap<>();
    private final Set<String> descriptionStems = new HashSet<>();

    LexiconWords(NutritionDataset.Lexicons lexicons, TextAnalysis analysis) {
        this.analysis = analysis;
        for (var lexicon : lexicons.lexicons()) {
            lexicon.units().values().forEach(words -> words.forEach(word -> singleWord(word)
                    .ifPresent(unitWords::add)));
            lexicon.states().forEach((state, words) -> words.forEach(word -> singleWord(word)
                    .ifPresent(single -> {
                        stateWords.add(single);
                        analysis.stems(single, lexicon.language()).forEach(stem -> statesByStem.put(stem, state));
                    })));
            lexicon.descriptions().forEach(word -> singleWord(word)
                    .ifPresent(single -> descriptionStems.addAll(analysis.stems(single, lexicon.language()))));
        }
    }

    boolean isUnit(String word) {
        return unitWords.contains(word);
    }

    boolean isDescription(String word) {
        return analysis.stems(word).stream().anyMatch(descriptionStems::contains);
    }

    Optional<String> state(String word) {
        return analysis.stems(word).stream().map(statesByStem::get).filter(Objects::nonNull).findFirst();
    }

    /** For splitting compounds like "knoblauchzehe". */
    Set<String> words() {
        var words = new HashSet<>(unitWords);
        words.addAll(stateWords);
        return words;
    }

    private Optional<String> singleWord(String entry) {
        var words = analysis.words(entry);
        return words.size() == 1 ? Optional.of(words.get(0)) : Optional.empty();
    }
}
