package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.List;
import java.util.regex.Pattern;

import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.ingredients.Amounts;

/** One written line made ready for IngredientExtractor, as one line per ingredient. */
final class IngredientLines {

    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\(\\s*\\)");
    private static final Pattern PLUS = Pattern.compile("\\s+\\+\\s+");
    private static final Pattern LIST_SEPARATOR = Pattern.compile("\\s*,\\s*|\\s+/\\s+");
    private static final int MIN_LIST_ITEMS = 3;
    private static final int MAX_LIST_ITEM_WORDS = 3;
    /** "Mehl: 1 EL", "Butter - 60 g". */
    private static final Pattern AMOUNT_LAST = Pattern.compile(
            "^(?<name>\\D.*?)\\s*[-:\\u2013\\u2014]\\s*(?<amount>" + Amounts.REGEX + Amounts.OPTIONAL_RANGE_END
                    + ")\\s*(?<unit>\\p{L}[\\p{L}.]*)?$");

    private final UnitLexicon unitLexicon;

    IngredientLines(UnitLexicon unitLexicon) {
        this.unitLexicon = unitLexicon;
    }

    List<String> clean(String line) {
        var text = Lines.MENTION.matcher(Lines.withoutBullet(line)).replaceAll("");
        text = IngredientNames.tidy(EMPTY_BRACKETS.matcher(text).replaceAll(""));
        return Lines.splitOutsideBrackets(text, PLUS).stream()
                .flatMap(part -> splitList(part).stream())
                .map(this::amountFirst)
                .filter(item -> !item.isBlank())
                .toList();
    }

    /** Only short names without an amount: "Fresh basil, sliced" stays one line. */
    private static List<String> splitList(String text) {
        if (Lines.startsWithQuantity(text)) {
            return List.of(text);
        }
        var items = Lines.splitOutsideBrackets(text, LIST_SEPARATOR);
        var isList = items.size() >= MIN_LIST_ITEMS
                && items.stream().allMatch(item -> Lines.wordCount(item) <= MAX_LIST_ITEM_WORDS);
        return isList ? items : List.of(text);
    }

    private String amountFirst(String text) {
        var matcher = AMOUNT_LAST.matcher(text);
        if (!matcher.matches()) {
            return text;
        }
        var unit = matcher.group("unit");
        if (unit != null && !unitLexicon.isKnownUnit(unit)) {
            return text;
        }
        return IngredientNames.tidy(matcher.group("amount") + " " + (unit == null ? "" : unit) + " "
                + matcher.group("name"));
    }
}
