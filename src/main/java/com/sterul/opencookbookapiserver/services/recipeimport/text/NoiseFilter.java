package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sterul.opencookbookapiserver.services.recipeimport.Links;

/** Links are kept aside: a caption without a recipe may point to one. */
final class NoiseFilter {

    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]+");
    /** "[Snack, Potato wedges, Viral]" */
    private static final Pattern TAG_LIST = Pattern.compile("^[\\[{].*,.*[\\]}]$");
    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final int MAX_WORDS_BESIDE_MENTIONS = 1;

    private NoiseFilter() {
    }

    record Cleaned(List<String> lines, String link) {
    }

    static Cleaned clean(List<String> lines) {
        var link = lines.stream().flatMap(line -> Links.in(line).stream()).findFirst().orElse(null);
        var withoutLinks = lines.stream().map(line -> Links.without(line).trim()).toList();
        var body = withoutLinks.subList(0, startOfTrailingTags(withoutLinks));

        var kept = new ArrayList<String>();
        var belowNutritionHeading = false;
        for (var line : body) {
            if (line.isEmpty() || isTagLine(line)) {
                kept.add("");
                continue;
            }
            if (RecipeTextLexicon.isNutritionHeading(line)) {
                belowNutritionHeading = true;
                continue;
            }
            if (RecipeTextLexicon.isNutritionLine(line, belowNutritionHeading)) {
                continue;
            }
            belowNutritionHeading = false;
            var text = withoutNoise(line);
            if (!text.isBlank()) {
                kept.add(text);
            }
        }
        return new Cleaned(kept, link);
    }

    private static String withoutNoise(String line) {
        var text = withoutTrailingHashtags(line);
        if (Lines.startsWithQuantity(text)) {
            // An ingredient line, even one that names a brand's @handle.
            return text;
        }
        text = withoutCallsToAction(text);
        return isMentionLine(text) ? "" : text;
    }

    /** Whatever follows the closing tags goes too; tags above the ingredients are no end. */
    private static int startOfTrailingTags(List<String> lines) {
        var start = lines.size();
        for (var i = lines.size() - 1; i >= 0; i--) {
            var line = lines.get(i);
            if (isTagLine(line)) {
                start = i;
            } else if (Lines.startsWithQuantity(line) || RecipeTextLexicon.ingredientHeading(line).isPresent()
                    || RecipeTextLexicon.stepHeading(line).isPresent()) {
                break;
            }
        }
        return start;
    }

    private static String withoutTrailingHashtags(String line) {
        var words = line.split(" ");
        var end = words.length;
        while (end > 0 && HASHTAG.matcher(words[end - 1]).matches()) {
            end--;
        }
        return String.join(" ", Arrays.copyOf(words, end));
    }

    private static boolean isTagLine(String line) {
        var words = Lines.wordCount(line);
        return TAG_LIST.matcher(line).matches()
                || (words > 0 && HASHTAG.matcher(line).results().count() * 2 > words);
    }

    private static boolean isMentionLine(String text) {
        var beside = NOT_A_WORD.matcher(Lines.MENTION.matcher(text).replaceAll(" ")).replaceAll(" ");
        return Lines.MENTION.matcher(text).find() && Lines.wordCount(beside) <= MAX_WORDS_BESIDE_MENTIONS;
    }

    /** Per sentence and emoji: "Mango Toast 😍 save it for later!" keeps its title. */
    private static String withoutCallsToAction(String line) {
        var segments = Lines.emojiSegments(line).stream()
                .flatMap(Lines::sentences)
                .toList();
        if (segments.stream().noneMatch(NoiseFilter::isNoise)) {
            return line;
        }
        return segments.stream()
                .filter(segment -> !isNoise(segment))
                .collect(Collectors.joining(" "));
    }

    private static boolean isNoise(String segment) {
        return RecipeTextLexicon.isCallToAction(segment) || isMentionLine(segment);
    }
}
