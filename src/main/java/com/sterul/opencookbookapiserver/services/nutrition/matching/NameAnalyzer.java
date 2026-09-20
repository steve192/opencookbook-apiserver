package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** For finding candidates, in every language: the name's language is unknown. */
    AnalyzedName typed(String name) {
        return typed(name, analysis::stems);
    }

    /**
     * For comparing with a catalogue name of one language, stemmed in that language only: a German plural must not
     * meet an English word of the same stem ("Beeren", "beer").
     */
    AnalyzedName typed(String name, String language) {
        return typed(name, word -> analysis.stems(word, language));
    }

    private AnalyzedName typed(String name, Function<String, Set<String>> stemsOf) {
        var words = new ArrayList<AnalyzedWord>();
        var states = new HashSet<String>();
        for (var word : analysis.typedWords(name)) {
            if (TextAnalysis.isNumber(word)) {
                words.add(new AnalyzedWord(word, Set.of(word), List.of(), false));
            } else if (!analysis.isStopword(word)) {
                var state = lexicon.state(word);
                if (state.isPresent()) {
                    states.add(state.get());
                } else if (!lexicon.isUnit(word)) {
                    words.add(typedWord(word, states, stemsOf));
                }
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
            var state = lexicon.state(word);
            if (state.isPresent()) {
                states.add(state.get());
            } else {
                words.add(cataloguedWord(word, stemsOf));
            }
        }
        var qualifiers = cataloguedWords.qualifiers().stream()
                .filter(word -> TextAnalysis.isNumber(word) || !analysis.isStopword(word))
                .map(word -> new AnalyzedWord(word, TextAnalysis.isNumber(word) ? Set.of(word) : stemsOf.apply(word), List.of(), false))
                .toList();
        return new AnalyzedName(name, words, qualifiers, states);
    }

    /** State parts of a compound add to {@code states}. Whether a word names a food is judged in every language. */
    private AnalyzedWord typedWord(String word, Set<String> states, Function<String, Set<String>> stemsOf) {
        var parts = splitter.split(word).stream().flatMap(List::stream)
                .map(part -> typedPart(part, splitter.isKnown(word), states, stemsOf))
                .toList();
        return new AnalyzedWord(word, stemsOf.apply(word), parts, isFoodWord(analysis.stems(word)), lexicon.isDescription(word));
    }

    private AnalyzedWord cataloguedWord(String word, Function<String, Set<String>> stemsOf) {
        var stems = stemsOf.apply(word);
        var parts = splitter.splitNamingPart(word).stream().flatMap(List::stream)
                .map(part -> {
                    var partStems = stemsOf.apply(part);
                    return namePart(part, partStems, isFoodWord(partStems));
                })
                .toList();
        return new AnalyzedWord(word, stems, parts, isFoodWord(stems));
    }

    private AnalyzedWord.Part typedPart(String text, boolean wordIsKnown, Set<String> states,
            Function<String, Set<String>> stemsOf) {
        var stems = stemsOf.apply(text);
        if (!wordIsKnown) {
            var state = lexicon.state(text);
            if (state.isPresent()) {
                states.add(state.get());
                return new AnalyzedWord.Part(text, stems, AnalyzedWord.Kind.STATE, false);
            }
            if (lexicon.isUnit(text)) {
                return new AnalyzedWord.Part(text, stems, AnalyzedWord.Kind.UNIT, false);
            }
        }
        return namePart(text, stems, isFoodWord(analysis.stems(text)));
    }

    private static AnalyzedWord.Part namePart(String text, Set<String> stems, boolean foodWord) {
        return new AnalyzedWord.Part(text, stems, AnalyzedWord.Kind.NAME, foodWord);
    }

    private boolean isFoodWord(Set<String> stems) {
        return stems.stream().anyMatch(foodWordStems::contains);
    }
}
