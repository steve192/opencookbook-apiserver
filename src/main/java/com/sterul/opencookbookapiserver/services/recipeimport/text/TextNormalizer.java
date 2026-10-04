package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/** Blank lines stay: they separate paragraphs. */
final class TextNormalizer {

    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n?|[\\u2028\\u2029\\u0085]");
    private static final Pattern KEYCAP_DIGIT = Pattern.compile("(\\d)\\uFE0F?\\u20E3");
    private static final Pattern KEYCAP_TEN = Pattern.compile("\\x{1F51F}");
    /** Before NFKC, which would turn one and a half into eleven halves. */
    private static final Pattern FRACTION_AFTER_DIGIT = Pattern.compile("(\\d)([\\u00BC-\\u00BE\\u2150-\\u215E])");
    private static final Pattern FRACTION_SLASH = Pattern.compile("\\u2044");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B\\u200C\\u2060-\\u2064\\uFEFF\\uFE0E\\uFE0F]");
    private static final Pattern BLANK_CHARACTER = Pattern.compile("[\\t\\u2800\\u3164]");
    private static final Pattern SPACES = Pattern.compile(" {2,}");
    /** A dot or emoji alone, written where Instagram would drop a blank line. */
    private static final Pattern NO_CONTENT = Pattern.compile("[^\\p{L}\\p{N}]*");

    private TextNormalizer() {
    }

    static List<String> lines(String text) {
        var normalized = LINE_BREAK.matcher(text).replaceAll("\n");
        normalized = KEYCAP_DIGIT.matcher(normalized).replaceAll("$1. ");
        normalized = KEYCAP_TEN.matcher(normalized).replaceAll("10. ");
        normalized = FRACTION_AFTER_DIGIT.matcher(normalized).replaceAll("$1 $2");
        // Fancy fonts, full width letters and vulgar fractions become plain ones.
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        normalized = FRACTION_SLASH.matcher(normalized).replaceAll("/");
        normalized = INVISIBLE.matcher(normalized).replaceAll("");
        normalized = BLANK_CHARACTER.matcher(normalized).replaceAll(" ");
        return normalized.lines()
                .map(line -> SPACES.matcher(line).replaceAll(" ").trim())
                .map(line -> NO_CONTENT.matcher(line).matches() ? "" : line)
                .toList();
    }
}
