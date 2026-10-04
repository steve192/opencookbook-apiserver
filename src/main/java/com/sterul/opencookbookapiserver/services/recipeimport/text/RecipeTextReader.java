package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;

/** Reads a recipe from a caption, a note or a message, by fixed German and English rules. */
@Component
public class RecipeTextReader {

    private static final int MAX_TITLE_WORDS = 8;
    /** "Crunchy Spitzkohl - unser Rezept der Woche". */
    private static final Pattern TEASER_SEPARATOR = Pattern.compile("\\s++[-|\\u2013\\u2014]\\s++");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.,;]+$");
    private static final Pattern LOWER_CASE = Pattern.compile("\\p{Ll}");

    private final IngredientLines ingredientLines;

    public RecipeTextReader(UnitLexicon unitLexicon) {
        this.ingredientLines = new IngredientLines(unitLexicon);
    }

    public RecipeText read(String text) {
        var cleaned = NoiseFilter.clean(TextNormalizer.lines(text));
        var lines = cleaned.lines();
        var block = IngredientBlock.find(lines).orElse(null);
        var ingredients = block == null ? List.<String>of() : block.lines().stream()
                .flatMap(line -> ingredientLines.clean(line).stream())
                .toList();
        if (ingredients.isEmpty()) {
            return RecipeText.none(cleaned.link());
        }
        var nextRecipe = startOfNextRecipe(lines, block.end());
        var steps = Steps.read(lines.subList(block.end(), nextRecipe));
        var title = title(lines, block.start(), nextRecipe < lines.size());
        return new RecipeText(title.orElse(null), servings(lines, block).orElse(null), ingredients, steps,
                cleaned.link());
    }

    /** An ingredient heading after the steps starts the next recipe, with the lines above it. */
    private static int startOfNextRecipe(List<String> lines, int from) {
        for (var i = from; i < lines.size(); i++) {
            if (RecipeTextLexicon.ingredientHeading(lines.get(i)).isPresent()) {
                var start = i;
                while (start > from && !lines.get(start - 1).isBlank()) {
                    start--;
                }
                return start;
            }
        }
        return lines.size();
    }

    /** A caption of several recipes opens with what they share: there the line above the ingredients wins. */
    private static Optional<String> title(List<String> lines, int ingredientsStart, boolean severalRecipes) {
        var first = IntStream.range(0, ingredientsStart).filter(i -> !lines.get(i).isBlank()).boxed().findFirst();
        var above = IntStream.iterate(ingredientsStart - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> !lines.get(i).isBlank()).boxed().findFirst();
        var candidates = severalRecipes ? Stream.of(above, first) : Stream.of(first, above);
        return candidates.flatMap(Optional::stream)
                .map(lines::get)
                .map(RecipeTextReader::titleOf)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> titleOf(String line) {
        var segment = Lines.emojiSegments(line).stream()
                .filter(part -> RecipeTextLexicon.ingredientHeading(part).isEmpty())
                .findFirst();
        if (segment.isEmpty()) {
            return Optional.empty();
        }
        var title = Lines.numbered(segment.get()).orElse(segment.get());
        title = TEASER_SEPARATOR.split(title, 2)[0];
        if (title.endsWith(":") || title.contains("?")) {
            return Optional.empty();
        }
        title = title.split("!", 2)[0].trim();
        // "Happy Hour at Home: Burrata with Olives" is a series name and the dish.
        if (Lines.wordCount(title) > MAX_TITLE_WORDS && title.contains(": ")) {
            title = title.substring(title.lastIndexOf(": ") + 2);
        }
        title = TRAILING_PUNCTUATION.matcher(title).replaceAll("");
        var isTitle = Lines.wordCount(title) > 0 && Lines.wordCount(title) <= MAX_TITLE_WORDS
                && !RecipeTextLexicon.isCallToAction(title) && !RecipeTextLexicon.isTeaser(title)
                && !Lines.startsWithQuantity(title) && RecipeTextLexicon.stepHeading(title).isEmpty();
        return isTitle ? Optional.of(withoutShouting(title)) : Optional.empty();
    }

    private static String withoutShouting(String title) {
        if (LOWER_CASE.matcher(title).find()) {
            return title;
        }
        return Arrays.stream(title.toLowerCase(Locale.ROOT).split(" "))
                .map(word -> word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static Optional<Integer> servings(List<String> lines, IngredientBlock block) {
        var inHeading = Optional.ofNullable(block.heading()).flatMap(RecipeTextLexicon::servingsInHeading);
        if (inHeading.isPresent()) {
            return inHeading.filter(servings -> servings > 0);
        }
        return lines.subList(0, block.end()).stream()
                .map(line -> RecipeTextLexicon.servings(Lines.plain(line)))
                .flatMap(Optional::stream)
                .filter(servings -> servings > 0)
                .findFirst();
    }
}
