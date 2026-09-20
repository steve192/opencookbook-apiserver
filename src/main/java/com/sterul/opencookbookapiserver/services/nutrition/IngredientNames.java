package com.sterul.opencookbookapiserver.services.nutrition;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalises names and phrases for lookup. */
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
}
