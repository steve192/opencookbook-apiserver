package com.sterul.opencookbookapiserver.services.nutrition;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalises names and phrases for lookup. */
public final class IngredientNames {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private IngredientNames() {
    }

    /** Trimmed and single-spaced. */
    public static String tidy(String name) {
        return WHITESPACE.matcher(name.trim()).replaceAll(" ");
    }

    /** Tidy and lower case. */
    public static String normalise(String name) {
        return tidy(name).toLowerCase(Locale.ROOT);
    }
}
