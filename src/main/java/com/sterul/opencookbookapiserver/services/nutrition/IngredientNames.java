package com.sterul.opencookbookapiserver.services.nutrition;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalises names and phrases for lookup: trimmed, single-spaced, lower case. */
public final class IngredientNames {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private IngredientNames() {
    }

    public static String normalise(String name) {
        return WHITESPACE.matcher(name.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
