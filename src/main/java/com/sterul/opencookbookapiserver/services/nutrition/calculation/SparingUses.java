package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

/** Recognises names of ingredients used only a little ("Mehl für die Form"), from the lexicons. */
@Component
public class SparingUses {

    private final List<Pattern> phrases;

    public SparingUses(NutritionDatasetReader reader) {
        phrases = reader.lexicons().lexicons().stream()
                .flatMap(lexicon -> lexicon.sparingUses().stream())
                .map(phrase -> Pattern.compile("(?<!\\p{L})" + Pattern.quote(IngredientNames.normalise(phrase)) + "(?!\\p{L})"))
                .toList();
    }

    public boolean isSparing(String ingredientName) {
        var name = IngredientNames.normalise(ingredientName);
        return phrases.stream().anyMatch(phrase -> phrase.matcher(name).find());
    }
}
