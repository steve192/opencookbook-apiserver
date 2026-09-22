package com.sterul.opencookbookapiserver.services.selection;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * The app's own-cookbook search (the npm package {@code fuzzy}), so both lists find the same recipes:
 * every character of the input in order, anywhere in the title. The word matcher in RecipeService
 * needs whole words and misses "gratin" in "Kartoffelgratin".
 */
public final class FuzzyTitleSearch {

    private FuzzyTitleSearch() {
    }

    private record Scored<T>(T item, double score) {
    }

    /** The items whose title matches, best match first; equal matches keep their order. */
    public static <T> List<T> filter(String input, List<T> items, Function<T, String> title) {
        var pattern = input.toLowerCase(Locale.ROOT);
        return items.stream()
                .flatMap(item -> score(pattern, title.apply(item)).stream()
                        .mapToObj(score -> new Scored<>(item, score)))
                .sorted(Comparator.comparingDouble(Scored<T>::score).reversed())
                .map(Scored::item)
                .toList();
    }

    /** Runs of consecutive characters count more than linearly; the whole title counts most. */
    private static OptionalDouble score(String pattern, String title) {
        var text = title.toLowerCase(Locale.ROOT);
        if (text.equals(pattern)) {
            return OptionalDouble.of(Double.POSITIVE_INFINITY);
        }
        var matched = 0;
        var run = 0.0;
        var total = 0.0;
        for (var index = 0; index < text.length(); index++) {
            if (matched < pattern.length() && text.charAt(index) == pattern.charAt(matched)) {
                matched++;
                run += 1 + run;
            } else {
                run = 0;
            }
            total += run;
        }
        return matched == pattern.length() ? OptionalDouble.of(total) : OptionalDouble.empty();
    }
}
