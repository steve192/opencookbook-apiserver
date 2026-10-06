package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sterul.opencookbookapiserver.services.ingredients.Amounts;

final class Lines {

    static final Pattern MENTION = Pattern.compile("@[\\p{L}\\p{N}_.]+");

    /** Not the digits, which are emoji too. */
    private static final Pattern EMOJI = Pattern.compile(
            "[\\p{IsExtended_Pictographic}\\p{IsEmoji_Component}&&[^#*0-9]]+");
    private static final String BULLETS = "-*+>\\u2022\\u00B7\\u2013\\u2014\\u2192\\u25B8\\u2713";
    private static final Pattern BULLET = Pattern.compile("^[" + BULLETS + "][" + BULLETS + "\\s]*");
    /** "1.", "2)", "Step 3:" but not "1.5 tbsp". */
    private static final Pattern NUMBERED = Pattern.compile(
            "^(?:(?:schritt|step)\\s*\\d{1,2}\\s*[.):]?|\\d{1,2}[.)](?!\\d))\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern QUANTITY = Pattern.compile("^" + Amounts.REGEX);
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("[.!?]\\s+\\p{Lu}");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?][\"'\\u201C\\u201D]*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final int MAX_INGREDIENT_LINE_WORDS = 10;

    private Lines() {
    }

    static String plain(String line) {
        return WHITESPACE.matcher(EMOJI.matcher(line).replaceAll(" ")).replaceAll(" ").trim();
    }

    static Stream<String> sentences(String text) {
        return SENTENCE_BOUNDARY.splitAsStream(text);
    }

    static List<String> emojiSegments(String line) {
        return Arrays.stream(EMOJI.split(line)).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    static boolean hasBullet(String line) {
        var plain = plain(line);
        return BULLET.matcher(plain).find() && numbered(line).isEmpty() || startsWithEmoji(line);
    }

    static String withoutBullet(String line) {
        return BULLET.matcher(plain(line)).replaceFirst("");
    }

    static Optional<String> numbered(String line) {
        var matcher = NUMBERED.matcher(withoutBullet(line));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static boolean startsWithQuantity(String line) {
        return numbered(line).isEmpty() && QUANTITY.matcher(withoutBullet(line)).find();
    }

    static boolean isProse(String line) {
        if (startsWithQuantity(line)) {
            return false;
        }
        var text = withoutBullet(line);
        return SENTENCE_END.matcher(text).find() || SENTENCE_BREAK.matcher(text).find()
                || wordCount(text) > MAX_INGREDIENT_LINE_WORDS;
    }

    static boolean isQuestion(String line) {
        return withoutBullet(line).replaceAll("[\"')\\]]+$", "").endsWith("?");
    }

    static int wordCount(String text) {
        var trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : WHITESPACE.split(trimmed).length;
    }

    /** "Joghurt, Beeren (Himbeeren, Brombeeren)" is two parts. */
    static List<String> splitOutsideBrackets(String text, Pattern separator) {
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

    static Optional<Integer> nextNonBlank(List<String> lines, int after) {
        for (var i = after + 1; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWithEmoji(String line) {
        return EMOJI.matcher(line.trim()).lookingAt();
    }
}
