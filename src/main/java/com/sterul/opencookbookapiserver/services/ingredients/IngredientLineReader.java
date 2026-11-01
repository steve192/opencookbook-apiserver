package com.sterul.opencookbookapiserver.services.ingredients;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.catalogue.matching.MatchCandidate;

/**
 * Reads an ingredient line as recipes write it ("1 1/2 EL Butter, weich") into amount, unit, food name and note. Only
 * units the lexicon knows count, so "4er Pack Eier" stays a name. What follows a comma or stands in brackets is a note,
 * part by part, unless the catalogue says the part singles out the food ("Tomaten, passiert" is passata, not tomatoes).
 */
@Component
public class IngredientLineReader {

    private static final Pattern BULLET = Pattern.compile("^[-\\u2013\\u2022*]+\\s*");
    /** Of a range, the lower bound; "1x Lauch" counts too. */
    private static final Pattern LEADING_AMOUNT = Pattern.compile(
            "^(" + Amounts.REGEX + ")" + Amounts.OPTIONAL_RANGE_END + "(?:\\s*[x×](?=\\s))?");
    /** "Mehl (ca. 200 g)" */
    private static final Pattern AMOUNT_IN_BRACKETS = Pattern.compile(
            "\\s*\\((?:ca\\.\\s*)?(?<amount>" + Amounts.REGEX + ")\\s*(?<unit>[^()\\d\\s][^()]*)\\)$");
    /** "Käse, ca. 100 g" */
    private static final Pattern AMOUNT_AFTER_COMMA = Pattern.compile(
            "\\s*,\\s*(?:ca\\.\\s*)?(?<amount>" + Amounts.REGEX + ")\\s*(?<unit>[^,\\d\\s][^,]*)$");
    /** "Mehl 200 g", "Mehl: 1 EL", "Eier - 3"; without a unit or a separator, "Weizenmehl 550" keeps its number. */
    private static final Pattern AMOUNT_AT_THE_END = Pattern.compile(
            "(?:(?<separator>\\s*+[-:\\u2013\\u2014]\\s*+)|\\s++)(?<amount>" + Amounts.REGEX + ")"
                    + Amounts.OPTIONAL_RANGE_END + "(?:\\s*+(?<unit>\\p{L}[\\p{L}.]*+))?$");
    /** The first comma, or a bracket after a space: "Zwiebel(n)" is one word. */
    private static final Pattern NOTE_START = Pattern.compile("\\s*+,\\s*+|\\s++(\\()");
    private static final Pattern NOTE_PARTS = Pattern.compile("\\s*+,\\s*+");
    /** Bounds the catalogue lookups per line; later parts are note. */
    private static final int JUDGED_NOTE_PARTS = 3;
    /** "EL gehäuft", "kl. Dose/n" */
    private static final int LONGEST_UNIT = 3;

    private final UnitLexicon unitLexicon;
    private final CatalogueMatcher matcher;

    public IngredientLineReader(UnitLexicon unitLexicon, CatalogueMatcher matcher) {
        this.unitLexicon = unitLexicon;
        this.matcher = matcher;
    }

    /** @param amount null if the line states none; unit and note are empty where it has none */
    public record IngredientLine(Float amount, String unit, String name, String note) {

        /** As a line of a recipe, its ingredient known by name only. */
        public IngredientNeed toNeed() {
            return IngredientNeed.detached(amount, unit, name, note);
        }
    }

    private record Quantity(Float amount, String unit, String rest) {
    }

    /**
     * @param language likely language of the line, as linking uses; null if unknown
     * @return empty where the line names no ingredient ("200 g")
     */
    public Optional<IngredientLine> read(String written, String language) {
        var text = withoutBullet(written);
        var quantity = quantityIn(text).orElse(new Quantity(null, "", text));
        var named = named(quantity.amount(), quantity.unit(), quantity.rest(), language);
        // "Prise" alone: the word is the name
        return named.isEmpty() && quantity.amount() == null ? named(null, "", text, language) : named;
    }

    /**
     * For sources that give amount and unit apart, as Chefkoch and the scan do.
     *
     * @param note what the source says beside the name, as "weich" or "(Type 405)"; may be blank
     */
    public Optional<IngredientLine> read(Float amount, String unit, String name, String note, String language) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        var extra = note == null ? "" : withoutSeparatorsAtTheEnds(note);
        if (extra.startsWith("(") && extra.endsWith(")")) {
            extra = extra.substring(1, extra.length() - 1);
        }
        var tidyUnit = unit == null ? "" : IngredientNames.tidy(unit);
        return named(amount, tidyUnit, IngredientNames.tidy(written(name, extra)), language);
    }

    /** A line's name and note as written: "Butter, weich". */
    public static String written(String name, String note) {
        return note.isEmpty() ? name : name + ", " + note;
    }

    /**
     * The amount and unit a name holds ("200g Schmelzkäse"); the rest stays the name, notes and all.
     *
     * @return empty where the name holds neither, or nothing besides them
     */
    public Optional<IngredientLine> quantityOf(String name) {
        return quantityIn(withoutBullet(name))
                .map(quantity -> new IngredientLine(quantity.amount(), quantity.unit(),
                        withoutSeparatorsAtTheEnds(quantity.rest()), ""))
                .filter(line -> !line.name().isEmpty());
    }

    private Optional<Quantity> quantityIn(String text) {
        return leading(text).or(() -> trailing(text));
    }

    private Optional<Quantity> leading(String text) {
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
        return Optional.of(new Quantity(amount, unit.map(IngredientLineReader::withoutComma).orElse(""), rest));
    }

    private Optional<Quantity> trailing(String text) {
        return inBrackets(text, AMOUNT_IN_BRACKETS)
                .or(() -> inBrackets(text, AMOUNT_AFTER_COMMA))
                .or(() -> atTheEnd(text));
    }

    private Optional<Quantity> inBrackets(String text, Pattern pattern) {
        var match = pattern.matcher(text);
        if (!match.find()) {
            return Optional.empty();
        }
        var unit = match.group("unit").trim();
        return unitLexicon.isKnownUnit(unit)
                ? Optional.of(new Quantity(Amounts.parse(match.group("amount")), unit, text.substring(0, match.start())))
                : Optional.empty();
    }

    private Optional<Quantity> atTheEnd(String text) {
        var match = AMOUNT_AT_THE_END.matcher(text);
        // A name starting with a digit ("7-Up") is no name before an amount
        if (!match.find() || match.start() == 0 || Character.isDigit(text.charAt(0))) {
            return Optional.empty();
        }
        var unit = match.group("unit");
        var isAmount = unit == null ? match.group("separator") != null : unitLexicon.isKnownUnit(unit);
        if (!isAmount) {
            return Optional.empty();
        }
        return Optional.of(new Quantity(Amounts.parse(match.group("amount")), unit == null ? "" : unit,
                text.substring(0, match.start())));
    }

    private Optional<IngredientLine> named(Float amount, String unit, String rest, String language) {
        var text = withoutSeparatorsAtTheEnds(rest);
        var noteStart = NOTE_START.matcher(text);
        if (!noteStart.find()) {
            return text.isEmpty() ? Optional.empty() : Optional.of(new IngredientLine(amount, unit, text, ""));
        }
        var name = withoutSeparatorsAtTheEnds(text.substring(0, noteStart.start()));
        if (name.isEmpty()) {
            return Optional.empty();
        }
        var note = text.substring(noteStart.end());
        var parts = IngredientNames.splitOutsideBrackets(noteStart.group(1) == null ? note : note.replaceFirst("\\)", ""),
                NOTE_PARTS);
        if (parts.isEmpty()) {
            return Optional.of(new IngredientLine(amount, unit, name, ""));
        }
        var nameParts = new ArrayList<>(List.of(name));
        var noteParts = new ArrayList<String>();
        var food = foodOf(name, language);
        var judged = parts.subList(0, Math.min(parts.size(), JUDGED_NOTE_PARTS));
        for (var part : judged) {
            // A part singles out the food where the name with it links to another one
            var foodWithPart = foodOf(written(String.join(", ", nameParts), part), language);
            if (foodWithPart.isPresent() && !foodWithPart.equals(food)) {
                nameParts.add(part);
                food = foodWithPart;
            } else {
                noteParts.add(part);
            }
        }
        noteParts.addAll(parts.subList(judged.size(), parts.size()));
        // Where every part names the food, the name stays as written: "Spinat (TK)"
        return Optional.of(noteParts.isEmpty()
                ? new IngredientLine(amount, unit, text, "")
                : new IngredientLine(amount, unit, String.join(", ", nameParts), String.join(", ", noteParts)));
    }

    /** None while the catalogue is not ready. */
    private Optional<String> foodOf(String name, String language) {
        return matcher.match(name, language).map(MatchCandidate::foodKey);
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

    private static String withoutBullet(String written) {
        return BULLET.matcher(IngredientNames.tidy(written)).replaceFirst("");
    }

    private static boolean touchesTheAmount(String rest) {
        return !rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)) && rest.charAt(0) != ',';
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
