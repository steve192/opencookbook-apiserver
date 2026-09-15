package com.sterul.opencookbookapiserver.services.ingredients;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;

/**
 * Splits amount and unit off names like "200g Schmelzkäse", "Prise Zimt" or "Mehl (ca. 200 g)". Only units the
 * lexicon knows count, so "Gouda (48 % Fett)" and "4er Pack" stay names.
 */
@Component
public class IngredientNameSplitter {

    /** Of a range, the lower bound; "1x Lauch" counts too. */
    private static final Pattern LEADING_AMOUNT = Pattern.compile(
            "^(" + Amounts.REGEX + ")(?:\\s*[-–]\\s*" + Amounts.REGEX + ")?(?:\\s*[x×](?=\\s))?");
    private static final Pattern BULLET = Pattern.compile("^[-–•*]+\\s*");
    /** "Mehl (ca. 200 g)" */
    private static final Pattern AMOUNT_IN_BRACKETS = Pattern.compile(
            "\\s*\\((?:ca\\.\\s*)?(?<amount>" + Amounts.REGEX + ")\\s*(?<unit>[^()\\d\\s][^()]*)\\)$");
    /** "Käse, ca. 100 g" */
    private static final Pattern AMOUNT_AFTER_COMMA = Pattern.compile(
            "\\s*,\\s*(?:ca\\.\\s*)?(?<amount>" + Amounts.REGEX + ")\\s*(?<unit>[^,\\d\\s][^,]*)$");
    /** "EL gehäuft", "kl. Dose/n" */
    private static final int LONGEST_UNIT = 3;

    private final UnitLexicon unitLexicon;

    public IngredientNameSplitter(UnitLexicon unitLexicon) {
        this.unitLexicon = unitLexicon;
    }

    /** @param amount null if the name held none */
    public record SplitName(String name, Float amount, String unit) {
    }

    /** Empty if there is nothing to split off, or nothing would remain. */
    public Optional<SplitName> split(String written) {
        var text = BULLET.matcher(IngredientNames.tidy(written)).replaceFirst("");
        return leading(text)
                .or(() -> trailing(text, AMOUNT_IN_BRACKETS))
                .or(() -> trailing(text, AMOUNT_AFTER_COMMA));
    }

    private Optional<SplitName> leading(String text) {
        Float amount = null;
        var rest = text;
        var amountMatch = LEADING_AMOUNT.matcher(text);
        if (amountMatch.find()) {
            amount = Amounts.parse(amountMatch.group(1));
            rest = text.substring(amountMatch.end());
        }
        var unit = unitAt(rest.trim());
        // "200g", but not "4er Pack"
        if (amount != null && unit.isEmpty() && touchesTheAmount(rest)) {
            return Optional.empty();
        }
        if (unit.isPresent()) {
            rest = rest.trim().substring(unit.get().length());
        } else if (amount == null) {
            return Optional.empty();
        }
        return named(rest, amount, unit.map(IngredientNameSplitter::withoutComma).orElse(null));
    }

    private Optional<SplitName> trailing(String text, Pattern pattern) {
        var match = pattern.matcher(text);
        if (!match.find()) {
            return Optional.empty();
        }
        var unit = match.group("unit").trim();
        if (!unitLexicon.isKnownUnit(unit)) {
            return Optional.empty();
        }
        return named(text.substring(0, match.start()), Amounts.parse(match.group("amount")), unit);
    }

    /** The longest known unit the tidied text starts with. */
    private Optional<String> unitAt(String text) {
        var words = text.split(" ", LONGEST_UNIT + 1);
        for (var count = Math.min(LONGEST_UNIT, words.length); count > 0; count--) {
            var candidate = String.join(" ", Arrays.copyOf(words, count));
            if (!candidate.isEmpty() && unitLexicon.isKnownUnit(withoutComma(candidate))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean touchesTheAmount(String rest) {
        return !rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)) && rest.charAt(0) != ',';
    }

    private static Optional<SplitName> named(String rest, Float amount, String unit) {
        var name = withoutSeparatorsAtTheEnds(rest);
        return name.isEmpty() ? Optional.empty() : Optional.of(new SplitName(name, amount, unit));
    }

    /** A loop rather than a regex: "[\s,]+$" backtracks quadratically on long runs of separators. */
    private static String withoutSeparatorsAtTheEnds(String text) {
        var start = 0;
        var end = text.length();
        while (start < end && isSeparator(text.charAt(start))) {
            start++;
        }
        while (end > start && isSeparator(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isSeparator(char character) {
        return character == ',' || character == ' ';
    }

    private static String withoutComma(String unit) {
        return unit.endsWith(",") ? unit.substring(0, unit.length() - 1) : unit;
    }
}
