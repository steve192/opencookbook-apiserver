package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.List;
import java.util.regex.Pattern;

import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;

/** One written line made ready for the IngredientLineReader, as one line per ingredient. */
final class IngredientLines {

    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\(\\s*\\)");
    private static final Pattern PLUS = Pattern.compile("\\s+\\+\\s+");
    private static final Pattern LIST_SEPARATOR = Pattern.compile("\\s*,\\s*|\\s+/\\s+");
    private static final int MIN_LIST_ITEMS = 3;
    private static final int MAX_LIST_ITEM_WORDS = 3;

    private IngredientLines() {
    }

    static List<String> clean(String line) {
        var text = Lines.MENTION.matcher(Lines.withoutBullet(line)).replaceAll("");
        text = IngredientNames.tidy(EMPTY_BRACKETS.matcher(text).replaceAll(""));
        return IngredientNames.splitOutsideBrackets(text, PLUS).stream()
                .flatMap(part -> splitList(part).stream())
                .filter(item -> !item.isBlank())
                .toList();
    }

    /** Only short names without an amount: "Fresh basil, sliced" stays one line. */
    private static List<String> splitList(String text) {
        if (Lines.startsWithQuantity(text)) {
            return List.of(text);
        }
        var items = IngredientNames.splitOutsideBrackets(text, LIST_SEPARATOR);
        var isList = items.size() >= MIN_LIST_ITEMS
                && items.stream().allMatch(item -> Lines.wordCount(item) <= MAX_LIST_ITEM_WORDS);
        return isList ? items : List.of(text);
    }
}
