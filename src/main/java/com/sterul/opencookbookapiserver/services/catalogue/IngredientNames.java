package com.sterul.opencookbookapiserver.services.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalises names and phrases for lookup, and splits them into parts. */
public final class IngredientNames {

    /** Websites put non-breaking and thin spaces between an amount and its unit. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Zs}]+");

    private IngredientNames() {
    }

    /** Trimmed and single-spaced. */
    public static String tidy(String name) {
        return WHITESPACE.matcher(name).replaceAll(" ").trim();
    }

    /** Tidy and lower case. */
    public static String normalise(String name) {
        return tidy(name).toLowerCase(Locale.ROOT);
    }

    /** "Joghurt, Beeren (Himbeeren, Brombeeren)" is two parts. */
    public static List<String> splitOutsideBrackets(String text, Pattern separator) {
        var parts = new ArrayList<String>();
        var depth = 0;
        var start = 0;
        for (var i = 0; i < text.length(); i++) {
            var character = text.charAt(i);
            if (character == '(' || character == '[') {
                depth++;
            } else if ((character == ')' || character == ']') && depth > 0) {
                depth--;
            } else if (depth == 0) {
                var match = separator.matcher(text).region(i, text.length());
                if (match.lookingAt()) {
                    parts.add(text.substring(start, i).trim());
                    start = match.end();
                    i = start - 1;
                }
            }
        }
        parts.add(text.substring(start).trim());
        return parts.stream().filter(part -> !part.isEmpty()).toList();
    }
}
