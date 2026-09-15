package com.sterul.opencookbookapiserver.services.ingredients;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Amounts as recipes write them: "2", "1,5", "1/2", "½", "1½" or "1 1/2". */
public final class Amounts {

    /** Matches one amount; possessive, so it never backtracks. */
    public static final String REGEX = "(?:\\d++(?:[.,]\\d++)?(?:/\\d++|\\s++\\d++/\\d++|\\s*+\\p{No})?|\\p{No})";

    private static final Pattern WHITESPACE = Pattern.compile("\\s++");
    private static final Pattern VULGAR_FRACTION = Pattern.compile("\\p{No}");
    private static final Pattern NUMBER_WITH_VULGAR_FRACTION = Pattern.compile("(\\d++(?:\\.\\d++)?)(\\p{No})");
    private static final String FRACTION_SLASH = "⁄";

    private Amounts() {
    }

    /**
     * @param written the amount alone
     * @throws NumberFormatException if it is no amount
     */
    public static float parse(String written) {
        var amount = written.trim().replace(',', '.');
        var parts = WHITESPACE.split(amount);
        if (parts.length > 1) {
            return Float.parseFloat(parts[0]) + fraction(parts[1]);
        }
        var mixed = NUMBER_WITH_VULGAR_FRACTION.matcher(amount);
        if (mixed.matches()) {
            return Float.parseFloat(mixed.group(1)) + vulgarFraction(mixed.group(2));
        }
        return fraction(amount);
    }

    /** "1/2", "½" or a plain number. */
    private static float fraction(String part) {
        if (VULGAR_FRACTION.matcher(part).matches()) {
            return vulgarFraction(part);
        }
        if (part.contains("/")) {
            var terms = part.split("/");
            return Float.parseFloat(terms[0]) / Float.parseFloat(terms[1]);
        }
        return Float.parseFloat(part);
    }

    /** "½" decomposes to "1⁄2"; other number signs ("²") count as nothing. */
    private static float vulgarFraction(String fraction) {
        var terms = Normalizer.normalize(fraction, Normalizer.Form.NFKD).split(FRACTION_SLASH);
        if (terms.length != 2) {
            return 0F;
        }
        return (float) Integer.parseInt(terms[0]) / Integer.parseInt(terms[1]);
    }
}
