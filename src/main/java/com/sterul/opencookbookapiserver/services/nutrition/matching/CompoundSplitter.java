package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Splits compounds into dictionary words ("erdnuss-butter"), preferring the fewest parts. The word must be
 * covered completely, allowing one joining letter between parts and two ending letters ("schwein-e-fleisch",
 * "kartoffel-n"). Parts have at least {@value #SHORTEST_PART} letters; given short words ("öl") only at the edges.
 */
final class CompoundSplitter {

    static final int MOST_JOINING_LETTERS = 1;
    static final int MOST_ENDING_LETTERS = 2;
    static final int SHORTEST_PART = 3;
    static final int SHORTEST_KNOWN_EDGE = 4;

    private final Set<String> dictionary;

    CompoundSplitter(Set<String> dictionary, Set<String> shortParts) {
        this.dictionary = Stream.concat(dictionary.stream().filter(word -> word.length() >= SHORTEST_PART), shortParts.stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean mayBePart(String word, int start, int end) {
        var part = word.substring(start, end);
        if (part.length() == word.length() || !dictionary.contains(part)) {
            return false;
        }
        return part.length() >= SHORTEST_PART || start == 0 || end >= word.length() - MOST_ENDING_LETTERS;
    }

    boolean isKnown(String word) {
        return dictionary.contains(word);
    }

    /** Empty unless the word splits into at least two dictionary words. */
    Optional<List<String>> split(String word) {
        // fewest.get(i): fewest parts covering word[0..i); null if none.
        var fewest = new ArrayList<List<String>>(Collections.nCopies(word.length() + 1, null));
        fewest.set(0, List.of());
        for (var end = 1; end <= word.length(); end++) {
            fewest.set(end, fewestPartsEndingAt(word, end, fewest));
        }
        for (var end = word.length(); end >= Math.max(0, word.length() - MOST_ENDING_LETTERS); end--) {
            var parts = fewest.get(end);
            if (parts != null && parts.size() >= 2) {
                return Optional.of(parts);
            }
        }
        return Optional.empty();
    }

    /** Like {@link #split}, falling back to a known edge word plus an unknown rest ("speise-zwiebel"). */
    Optional<List<String>> splitNamingPart(String word) {
        var split = split(word);
        if (split.isPresent()) {
            return split;
        }
        for (var known = word.length() - SHORTEST_PART; known >= SHORTEST_KNOWN_EDGE; known--) {
            var rest = word.length() - known;
            if (dictionary.contains(word.substring(rest))) {
                return Optional.of(List.of(word.substring(0, rest), word.substring(rest)));
            }
            if (dictionary.contains(word.substring(0, known))) {
                return Optional.of(List.of(word.substring(0, known), word.substring(known)));
            }
        }
        return Optional.empty();
    }

    private List<String> fewestPartsEndingAt(String word, int end, List<List<String>> fewest) {
        List<String> best = null;
        for (var start = 0; start < end; start++) {
            if (!mayBePart(word, start, end)) {
                continue;
            }
            var part = word.substring(start, end);
            for (var gap = 0; gap <= Math.min(MOST_JOINING_LETTERS, start); gap++) {
                var before = fewest.get(start - gap);
                var leadingLetters = gap > 0 && start - gap == 0;
                if (before != null && !leadingLetters && (best == null || before.size() + 1 < best.size())) {
                    best = new ArrayList<>(before);
                    best.add(part);
                }
            }
        }
        return best;
    }
}
