package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Splits names into naming words and states; stopwords are dropped. Typed and catalogue names differ:
 * - typed names drop units ("Knoblauchzehen"); unit or state compound parts need no explaining unless the
 *   whole word is a catalogue word ("Glasnudeln");
 * - catalogue compounds may have an unknown part ("Speise-zwiebel"), typed words may not ("mehligkochend");
 * - numbers are words when typed, but qualifiers in catalogue names, like bracketed words.
 */
final class NameAnalyzer {

    private final TextAnalysis analysis;
    private final LexiconWords lexicon;
    private final CompoundSplitter splitter;
    private final Set<String> foodWordStems;

    NameAnalyzer(TextAnalysis analysis, LexiconWords lexicon, CompoundSplitter splitter, Set<String> foodWordStems) {
        this.analysis = analysis;
        this.lexicon = lexicon;
        this.splitter = splitter;
        this.foodWordStems = Set.copyOf(foodWordStems);
    }

    AnalyzedName typed(String name) {
        var words = new ArrayList<AnalyzedWord>();
        var states = new HashSet<String>();
        for (var word : analysis.typedWords(name)) {
            if (TextAnalysis.isNumber(word)) {
                words.add(new AnalyzedWord(word, Set.of(word), List.of(), false));
                continue;
            }
            if (analysis.isStopword(word)) {
                continue;
            }
            var state = lexicon.state(word, analysis);
            if (state.isPresent()) {
                states.add(state.get());
            } else if (!lexicon.isUnit(word)) {
                var stems = analysis.stems(word);
                var parts = splitter.split(word).stream().flatMap(List::stream)
                        .map(part -> typedPart(part, splitter.isKnown(word), states))
                        .toList();
                words.add(new AnalyzedWord(word, stems, parts, isFoodWord(stems)));
            }
        }
        return new AnalyzedName(name, words, List.of(), states);
    }

    AnalyzedName catalogued(String name, String language) {
        Function<String, Set<String>> stemsOf = word -> analysis.stems(word, language);
        var cataloguedWords = analysis.cataloguedWords(name);
        var words = new ArrayList<AnalyzedWord>();
        var states = new HashSet<String>();
        for (var word : cataloguedWords.words()) {
            if (analysis.isStopword(word)) {
                continue;
            }
            var state = lexicon.state(word, analysis);
            if (state.isPresent()) {
                states.add(state.get());
                continue;
            }
            var stems = stemsOf.apply(word);
            var parts = splitter.splitNamingPart(word).stream().flatMap(List::stream)
                    .map(part -> namePart(part, stemsOf.apply(part)))
                    .toList();
            words.add(new AnalyzedWord(word, stems, parts, isFoodWord(stems)));
        }
        var qualifiers = cataloguedWords.qualifiers().stream()
                .filter(word -> TextAnalysis.isNumber(word) || !analysis.isStopword(word))
                .map(word -> new AnalyzedWord(word, TextAnalysis.isNumber(word) ? Set.of(word) : stemsOf.apply(word), List.of(), false))
                .toList();
        return new AnalyzedName(name, words, qualifiers, states);
    }

    private AnalyzedWord.Part typedPart(String text, boolean wordIsKnown, Set<String> states) {
        var stems = analysis.stems(text);
        if (!wordIsKnown) {
            Optional<String> state = lexicon.state(text, analysis);
            if (state.isPresent()) {
                states.add(state.get());
                return new AnalyzedWord.Part(text, stems, AnalyzedWord.Kind.STATE, false);
            }
            if (lexicon.isUnit(text)) {
                return new AnalyzedWord.Part(text, stems, AnalyzedWord.Kind.UNIT, false);
            }
        }
        return namePart(text, stems);
    }

    private AnalyzedWord.Part namePart(String text, Set<String> stems) {
        return new AnalyzedWord.Part(text, stems, AnalyzedWord.Kind.NAME, isFoodWord(stems));
    }

    private boolean isFoodWord(Set<String> stems) {
        return stems.stream().anyMatch(foodWordStems::contains);
    }
}
