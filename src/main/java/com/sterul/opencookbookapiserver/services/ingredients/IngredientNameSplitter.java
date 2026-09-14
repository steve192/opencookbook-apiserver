package com.sterul.opencookbookapiserver.services.ingredients;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;

/**
 * Splits amount and unit off names like "200g Schmelzkäse", "Prise Zimt" or "Mehl (ca. 200 g)". Only units the
 * lexicon knows count, so "Gouda (48 % Fett)" and "4er Pack" stay names.
 */
@Component
public class IngredientNameSplitter {

    /** Number, fraction or mixed number. */
    private static final String AMOUNT = "(?:\\d+/\\d+|\\d+(?:[.,]\\d+)?(?:\\s+\\d+/\\d+|\\p{No})?|\\p{No})";
    /** Of a range, the lower bound; "1x Lauch" counts too. */
    private static final Pattern LEADING_AMOUNT = Pattern.compile("^(" + AMOUNT + ")(?:\\s*[-–]\\s*" + AMOUNT + ")?(?:\\s*[x×](?=\\s))?");
    private static final Pattern BULLET = Pattern.compile("^[-–•*]+\\s*");
    /** "(ca. 200 g)" or ", ca. 200 g" closing the name. */
    private static final Pattern TRAILING_AMOUNT = Pattern.compile(
            "\\s*(?:\\((?:ca\\.\\s*)?(" + AMOUNT + ")\\s*([^()\\d\\s][^()]*?)\\)|,\\s*(?:ca\\.\\s*)?(" + AMOUNT + ")\\s*([^,\\d\\s][^,]*?))\\s*$");
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
        var text = BULLET.matcher(written.trim().replaceAll("\\s+", " ")).replaceFirst("");
        var leading = leading(text);
        if (leading.isPresent()) {
            return leading;
        }
        return trailing(text);
    }

    private Optional<SplitName> leading(String text) {
        Float amount = null;
        var rest = text;
        var amountMatch = LEADING_AMOUNT.matcher(text);
        if (amountMatch.find()) {
            amount = Amounts.parse(amountMatch.group(1));
            rest = text.substring(amountMatch.end());
            // "200g", but not "4er Pack"
            if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)) && rest.charAt(0) != ','
                    && unitAt(rest).isEmpty()) {
                return Optional.empty();
            }
        }
        var unit = unitAt(rest.trim());
        if (unit.isPresent()) {
            rest = rest.trim().substring(unit.get().length());
        }
        if (amount == null && unit.isEmpty()) {
            return Optional.empty();
        }
        return named(rest, amount, unit.map(IngredientNameSplitter::withoutComma).orElse(null));
    }

    private Optional<SplitName> trailing(String text) {
        var match = TRAILING_AMOUNT.matcher(text);
        if (!match.find()) {
            return Optional.empty();
        }
        var inBrackets = match.group(1) != null;
        var unit = (inBrackets ? match.group(2) : match.group(4)).trim();
        if (!unitLexicon.isKnownUnit(unit)) {
            return Optional.empty();
        }
        return named(text.substring(0, match.start()), Amounts.parse(inBrackets ? match.group(1) : match.group(3)), unit);
    }

    /** The longest known unit the text starts with. */
    private Optional<String> unitAt(String text) {
        var words = text.split("\\s+", LONGEST_UNIT + 1);
        for (var count = Math.min(LONGEST_UNIT, words.length); count > 0; count--) {
            var candidate = String.join(" ", Arrays.copyOf(words, count));
            if (!candidate.isEmpty() && unitLexicon.isKnownUnit(withoutComma(candidate))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<SplitName> named(String rest, Float amount, String unit) {
        var name = rest.replaceFirst("^[\\s,]+", "").replaceFirst("[\\s,]+$", "").replaceAll("\\s+", " ");
        return name.isEmpty() ? Optional.empty() : Optional.of(new SplitName(name, amount, unit));
    }

    private static String withoutComma(String unit) {
        return unit.endsWith(",") ? unit.substring(0, unit.length() - 1) : unit;
    }
}
