package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.ArrayList;
import java.util.List;

/** Word-by-word, order-independent comparison of a typed and a catalogue name. */
final class NameComparison {

    /** Fields as in {@link MatchFeatures}. */
    record Result(double queryCoverage, double candidateCoverage, boolean exact, double fuzzyShare,
            int unexplainedWords, int conflictingFoodWords, int otherFoodWords) {
    }

    /** A qualifier's weight in candidate coverage: breaks ties ("Brötchen" over "Brötchen (Blätterteig)"). */
    static final double QUALIFIER_WEIGHT = 0.25;

    private NameComparison() {
    }

    static Result compare(AnalyzedName typed, AnalyzedName catalogued) {
        var explaining = new ArrayList<>(catalogued.words());
        explaining.addAll(catalogued.qualifiers());
        var query = explainTyped(typed.words(), explaining);
        var candidate = explainCatalogued(catalogued, typed.words());
        return new Result(query.coverage(), candidate.coverage(), query.exact() && candidate.exact(), query.fuzzyShare(),
                query.unexplainedWords(), query.conflictingFoodWords(), candidate.otherFoodWords());
    }

    private record TypedSide(double coverage, double fuzzyShare, int unexplainedWords, int conflictingFoodWords, boolean exact) {
    }

    private record CataloguedSide(double coverage, int otherFoodWords, boolean exact) {
    }

    /**
     * A typed word naming nothing known is unexplained; one naming another food conflicts, and counts. A description
     * the candidate does not share is not held against it: "gehackte Petersilie" is still just parsley.
     */
    private static TypedSide explainTyped(List<AnalyzedWord> typedWords, List<AnalyzedWord> explaining) {
        var tally = new TypedTally();
        for (var word : typedWords) {
            tally.add(word, best(word, explaining));
        }
        return tally.toSide();
    }

    /** What the typed words added up to, one word at a time. */
    private static final class TypedTally {
        private double explained;
        private int counted;
        private double fuzzy;
        private int unexplained;
        private int conflicting;
        private boolean exact = true;

        private void add(AnalyzedWord word, Explanation best) {
            if (word.description() && (best.fraction() == 0 || best.fuzzy())) {
                // Not exact, though: a food whose name shares the description stays ahead.
                exact = false;
                return;
            }
            if (best.fraction() == 0) {
                addUnexplained(word);
                return;
            }
            explained += best.fraction();
            counted++;
            conflicting += best.unexplainedFoodParts();
            if (best.fuzzy()) {
                fuzzy += best.fraction();
            }
            exact &= best.whole();
        }

        private void addUnexplained(AnalyzedWord word) {
            exact = false;
            if (word.foodWord() || word.parts().stream().anyMatch(AnalyzedWord.Part::foodWord)) {
                conflicting++;
                counted++;
            } else {
                unexplained++;
            }
        }

        private TypedSide toSide() {
            var coverage = counted == 0 ? 0 : explained / counted;
            var fuzzyShare = explained == 0 ? 0 : fuzzy / explained;
            return new TypedSide(coverage, fuzzyShare, unexplained, conflicting, exact);
        }
    }

    private static CataloguedSide explainCatalogued(AnalyzedName catalogued, List<AnalyzedWord> typedWords) {
        var explained = 0.0;
        var otherFoodWords = 0;
        var exact = true;
        for (var word : catalogued.words()) {
            var best = best(word, typedWords);
            explained += best.fraction();
            exact &= best.whole();
            if (best.fraction() > 0) {
                otherFoodWords += best.unexplainedFoodParts();
            } else if (word.foodWord()) {
                otherFoodWords++;
            }
        }
        var size = (double) catalogued.words().size();
        for (var qualifier : catalogued.qualifiers()) {
            explained += QUALIFIER_WEIGHT * best(qualifier, typedWords).fraction();
            size += QUALIFIER_WEIGHT;
        }
        return new CataloguedSide(catalogued.words().isEmpty() ? 0 : explained / size, otherFoodWords, exact);
    }

    private static Explanation best(AnalyzedWord word, List<AnalyzedWord> others) {
        return others.stream()
                .map(other -> explain(word, other))
                .max((first, second) -> Double.compare(first.fraction(), second.fraction()))
                .orElse(Explanation.NONE);
    }

    private static Explanation explain(AnalyzedWord word, AnalyzedWord other) {
        if (word.sharesStemWith(other.stems())) {
            return Explanation.SAME_WORD;
        }
        var named = word.parts().stream().filter(AnalyzedWord.Part::needsExplaining).toList();
        if (!named.isEmpty()) {
            var explainedParts = named.stream().filter(part -> explains(other, part)).count();
            if (explainedParts > 0) {
                var unexplainedFoodParts = (int) named.stream().filter(part -> part.foodWord() && !explains(other, part)).count();
                return new Explanation((double) explainedParts / named.size(), false, false, unexplainedFoodParts);
            }
        }
        // "zwiebel" is a part of "speisezwiebel".
        if (other.parts().stream().anyMatch(part -> word.sharesStemWith(part.stems()))) {
            return new Explanation(1, false, false, 0);
        }
        var similarity = similarity(word, other);
        return similarity > 0 ? new Explanation(similarity, false, true, 0) : Explanation.NONE;
    }

    private static double similarity(AnalyzedWord word, AnalyzedWord other) {
        var best = TextAnalysis.similarity(word.text(), other.text());
        for (var stem : word.stems()) {
            for (var otherStem : other.stems()) {
                best = Math.max(best, TextAnalysis.similarity(stem, otherStem));
            }
        }
        return best;
    }

    private static boolean explains(AnalyzedWord other, AnalyzedWord.Part part) {
        return other.stems().stream().anyMatch(part.stems()::contains)
                || other.parts().stream().anyMatch(otherPart -> otherPart.stems().stream().anyMatch(part.stems()::contains));
    }

    /**
     * @param whole same word, not just a part or a look-alike
     * @param fuzzy explained only by similar spelling
     */
    private record Explanation(double fraction, boolean whole, boolean fuzzy, int unexplainedFoodParts) {
        static final Explanation NONE = new Explanation(0, false, false, 0);
        static final Explanation SAME_WORD = new Explanation(1, true, false, 0);
    }
}
