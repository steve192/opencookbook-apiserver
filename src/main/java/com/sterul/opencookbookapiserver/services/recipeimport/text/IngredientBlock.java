package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @param start   the heading's line, or the first line of a list without one
 * @param heading before its colon; null for a list without one
 * @param end     the first line after the ingredients
 */
record IngredientBlock(int start, String heading, List<String> lines, int end) {

    /** Without a heading, this many quantity lines in a row are an ingredient list. */
    private static final int MIN_QUANTITY_RUN = 3;
    private static final int MAX_GROUP_HEADING_WORDS = 5;
    private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");

    static Optional<IngredientBlock> find(List<String> lines) {
        for (var i = 0; i < lines.size(); i++) {
            var heading = RecipeTextLexicon.ingredientHeading(lines.get(i));
            if (heading.isPresent()) {
                return Optional.of(read(lines, i, i + 1, heading.get()));
            }
        }
        return firstQuantityRun(lines).map(start -> read(lines, start, start, null));
    }

    private static IngredientBlock read(List<String> lines, int start, int from, RecipeTextLexicon.Heading heading) {
        var collected = new ArrayList<>(heading == null ? List.<String>of() : itemsAfterColon(heading));
        var end = from;
        var afterBlank = false;
        for (var i = from; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.isBlank()) {
                afterBlank = true;
                continue;
            }
            if (startsTheMethod(line)) {
                break;
            }
            var next = Lines.nextNonBlank(lines, i).map(lines::get).orElse(null);
            var nested = RecipeTextLexicon.ingredientHeading(line);
            if (nested.isPresent()) {
                collected.addAll(itemsAfterColon(nested.get()));
            } else if (afterBlank && !Lines.startsWithQuantity(line) && next != null && Lines.isProse(next)) {
                // A section the lexicon does not know, such as "Cooking Notes".
                break;
            } else if (!isGroupHeading(line, next)) {
                collected.add(line);
            }
            afterBlank = false;
            end = i + 1;
        }
        return new IngredientBlock(start, heading == null ? null : heading.label(), collected, end);
    }

    private static boolean startsTheMethod(String line) {
        return RecipeTextLexicon.stepHeading(line).isPresent() || Lines.numbered(line).isPresent()
                || Lines.isProse(line);
    }

    /** "Für die Sauce:", "For the dough", or a short line above bulleted ones. */
    private static boolean isGroupHeading(String line, String next) {
        var text = Lines.withoutBullet(line);
        if (Lines.startsWithQuantity(line) || Lines.wordCount(text) > MAX_GROUP_HEADING_WORDS) {
            return false;
        }
        if (text.endsWith(":") || RecipeTextLexicon.startsAGroup(line)) {
            return true;
        }
        return !Lines.hasBullet(line) && next != null && Lines.hasBullet(next) && !Lines.isProse(next);
    }

    private static List<String> itemsAfterColon(RecipeTextLexicon.Heading heading) {
        return heading.inline().isBlank() ? List.of() : Lines.splitOutsideBrackets(heading.inline(), COMMA);
    }

    private static Optional<Integer> firstQuantityRun(List<String> lines) {
        var run = 0;
        for (var i = 0; i < lines.size(); i++) {
            run = Lines.startsWithQuantity(lines.get(i)) ? run + 1 : 0;
            if (run == MIN_QUANTITY_RUN) {
                return Optional.of(i - MIN_QUANTITY_RUN + 1);
            }
        }
        return Optional.empty();
    }
}
