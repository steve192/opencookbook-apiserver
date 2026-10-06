package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The German and English words the text reader knows. Units come from UnitLexicon. */
final class RecipeTextLexicon {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String APOSTROPHE = "['\\u2019]";

    /** "Zutaten", "Ingredients (4 servings)", "Zutaten für den Teig". */
    private static final Pattern INGREDIENT_HEADING = Pattern.compile(
            "(?:zutaten|ingredients?|ingrédients?)(?:\\s*\\(.*\\)|\\s+(?:für|for|pour)\\s.*)?", FLAGS);
    /** "Du brauchst:", "Here's what you'll need:", "Für 6 Waffeln benötigst du:". */
    private static final Pattern NEED = Pattern.compile(
            "\\b(?:du brauchst|ihr braucht|brauchst du|benötigst|you" + APOSTROPHE + "ll need|you need|what you need)\\b",
            FLAGS);
    private static final int MAX_NEED_HEADING_WORDS = 8;

    private static final Pattern STEP_HEADING = Pattern.compile(
            "(?:zubereitung|anleitung|schritte|so geht" + APOSTROPHE + "?s|so geht es|instructions|directions|method"
                    + "|steps|preparation|recipe|rezept)(?:\\s*\\(.*\\))?",
            FLAGS);

    /** "Für die Sauce", "For the dough", "Zum Dämpfen". */
    private static final Pattern GROUP_START = Pattern.compile("^(?:für|for|zum|zur)\\s", FLAGS);

    private static final Pattern CALL_TO_ACTION = Pattern.compile(String.join("|", List.of(
            "\\bcomment\\b", "kommentier", "kommentar", "\\b(?:will|" + APOSTROPHE + "ll|per|via) dm\\b", "\\binbox\\b",
            "\\bfollow (?:me|us|him|her|them|his|their|my|our|along|for|@)", "\\bfolg(?:e|t)? (?:mir|uns|@)",
            "\\bfolge\\b.*@", "abonnier", "\\bsubscribe\\b",
            "\\bsave (?:this|the (?:post|video|reel|recipe)|it for later|for later)",
            "\\bspeicher(?:e|t|n)? (?:dir|euch|es|das|den)", "abspeichern",
            "link in (?:my |our |the )?(?:bio|profile)", "link in (?:der|meiner|unserer) bio", "\\btap the link",
            "in (?:meinem|unserem) profil", "whatsapp",
            "\\bwerbung\\b", "\\banzeige\\b", "gewinnspiel", "verlosung", "\\bverlose", "\\bgiveaway\\b",
            "\\bsponsored\\b", "promo code", "discount code", "code promo", "\\buse code\\b", "rabattcode",
            "gutscheincode", "\\bcode\\s*:",
            "\\btag (?:a|your) (?:friend|bestie|someone)", "\\bmarkier(?:e|t)\\b",
            "\\b(?:by|von|via|credits?:?)\\s*@")), FLAGS);

    private static final Pattern CLOSER = Pattern.compile(
            "^(?:guten appetit|lasst es euch schmecken|lass es dir schmecken|enjoy|bon app[eé]tit|happy cooking"
                    + "|viel spaß)\\b",
            FLAGS);
    private static final Pattern GREETING = Pattern.compile(
            "(?:hallo|hi|hey|hello|ihr lieben|liebe)(?:\\s+\\p{L}+){0,3}\\s*[,!.]?", FLAGS);
    /** "Hier kommt das Rezept", "Recipe below". */
    private static final Pattern TEASER = Pattern.compile(
            "\\b(?:zum rezept|hier kommt das rezept|rezept (?:unten|im kommentar)|recipe below|recipe in the comments"
                    + "|here" + APOSTROPHE + "?s the recipe)\\b",
            FLAGS);

    private static final String NUTRIENT = "(?:kcal|kalorien|calories|protein|eiweiß|kh|kohlenhydrate|carbs"
            + "|carbohydrates|fett|fat|ballaststoffe|fiber|fibre|zucker|sugar)";
    private static final String NUMBER = "\\d+(?:[.,]\\d+)?";
    /** "76g Protein", "Kalorien 562", "522 Calories". */
    private static final Pattern NUTRITION_FACT = Pattern.compile(
            NUMBER + "\\s*(?:g|mg|kcal|cal)?\\s*" + NUTRIENT + "\\b|\\b" + NUTRIENT + "\\s*:?\\s*" + NUMBER
                    + "\\s*(?:g|mg|kcal)?",
            FLAGS);
    private static final Pattern ENERGY = Pattern.compile("kcal|kalorien|calories", FLAGS);
    private static final Pattern NUTRITION_HEADING = Pattern.compile(
            "^(?:nährwerte|nutrition|nutritional|macros|makros)\\b", FLAGS);
    private static final int MAX_NUTRITION_HEADING_WORDS = 6;
    /** "All for 522 Calories" */
    private static final int MAX_WORDS_BESIDE_NUTRITION = 2;

    private static final List<Pattern> SERVINGS = List.of(
            Pattern.compile("\\b(?:für|for)\\s+(?:ca\\.?\\s*|etwa\\s+|about\\s+)?(\\d{1,3})\\s+"
                    + "(?:personen|portionen|people|persons|servings|portions)\\b", FLAGS),
            Pattern.compile("\\b(?:serves|makes|ergibt|yields?)\\s+(?:ca\\.?\\s*|about\\s+)?(\\d{1,3})\\b", FLAGS),
            Pattern.compile("\\b(\\d{1,3})\\s+(?:servings|portionen|personen|portions)\\b", FLAGS));
    private static final Pattern WHOLE_NUMBER = Pattern.compile(
            "(?<![\\d.,/])(\\d{1,3})(?![\\d.,/])(?!\\s*(?:cm|mm|inch|zoll)\\b)", FLAGS);

    private RecipeTextLexicon() {
    }

    /** @param inline after the colon: "Du brauchst: 200 g Joghurt, 1 EL Honig" */
    record Heading(String label, String inline) {
    }

    static Optional<Heading> ingredientHeading(String line) {
        var heading = heading(line);
        var label = heading.label();
        var isHeading = INGREDIENT_HEADING.matcher(label).matches()
                || (NEED.matcher(label).find() && Lines.wordCount(label) <= MAX_NEED_HEADING_WORDS && line.contains(":"));
        return isHeading ? Optional.of(heading) : Optional.empty();
    }

    /** With whatever follows its colon: "So geht's: Alles verrühren." */
    static Optional<String> stepHeading(String line) {
        var heading = heading(line);
        return STEP_HEADING.matcher(heading.label()).matches() ? Optional.of(heading.inline()) : Optional.empty();
    }

    static boolean startsAGroup(String line) {
        return GROUP_START.matcher(Lines.withoutBullet(line)).find();
    }

    static boolean isCallToAction(String text) {
        return CALL_TO_ACTION.matcher(text).find();
    }

    static boolean isCloser(String text) {
        return CLOSER.matcher(Lines.withoutBullet(text)).find();
    }

    static boolean isGreeting(String line) {
        return GREETING.matcher(Lines.withoutBullet(line)).matches();
    }

    static boolean isTeaser(String text) {
        return TEASER.matcher(text).find();
    }

    static boolean isNutritionHeading(String line) {
        var text = Lines.withoutBullet(line);
        return NUTRITION_HEADING.matcher(text).find() && Lines.wordCount(text) <= MAX_NUTRITION_HEADING_WORDS;
    }

    /** One fact alone counts only for energy or below a nutrition heading: "200 g Zucker" is no fact. */
    static boolean isNutritionLine(String line, boolean belowNutritionHeading) {
        var text = Lines.withoutBullet(line);
        var facts = NUTRITION_FACT.matcher(text).results().toList();
        if (facts.isEmpty()) {
            return false;
        }
        var beside = NUTRITION_FACT.matcher(text).replaceAll(" ").replaceAll("[^\\p{L}\\s]", " ");
        var counts = facts.size() > 1 || belowNutritionHeading || ENERGY.matcher(text).find();
        return counts && Lines.wordCount(beside) <= MAX_WORDS_BESIDE_NUTRITION;
    }

    /** "Zutaten (für 4 Portionen)", "Ingredients (makes 6)", "Zutaten für 8 Gläser" */
    static Optional<Integer> servingsInHeading(String label) {
        var matcher = WHOLE_NUMBER.matcher(label);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    static Optional<Integer> servings(String line) {
        return SERVINGS.stream()
                .map(pattern -> pattern.matcher(line))
                .filter(Matcher::find)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .findFirst();
    }

    private static Heading heading(String line) {
        var text = Lines.withoutBullet(line);
        var colon = text.indexOf(':');
        var label = colon < 0 ? text : text.substring(0, colon);
        var inline = colon < 0 ? "" : text.substring(colon + 1);
        // "Steps :-" has nothing after its colon but the dash.
        return new Heading(label.trim(), inline.replaceFirst("^[\\s\\-]+", "").trim());
    }
}
