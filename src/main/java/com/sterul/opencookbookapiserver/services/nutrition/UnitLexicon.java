package com.sterul.opencookbookapiserver.services.nutrition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

/** Units and their words in all shipped languages ("EL", "tbsp"); available even with nutrition disabled. */
@Component
public class UnitLexicon {

    private static final String PIECE = "piece";

    private final Map<String, NutritionDataset.Unit> unitsByKey;
    private final Map<String, NutritionDataset.Unit> unitsByWord;

    public UnitLexicon(NutritionDatasetReader reader) {
        unitsByKey = reader.units().units().stream()
                .collect(Collectors.toUnmodifiableMap(NutritionDataset.Unit::key, Function.identity()));
        var byWord = new HashMap<String, NutritionDataset.Unit>();
        for (var lexicon : reader.lexicons().lexicons()) {
            lexicon.units().forEach((key, words) -> words.forEach(word -> byWord.put(IngredientNames.normalise(word), unitsByKey.get(key))));
        }
        unitsByWord = Map.copyOf(byWord);
    }

    public Optional<NutritionDataset.Unit> resolve(String word) {
        return word == null ? Optional.empty() : Optional.ofNullable(unitsByWord.get(IngredientNames.normalise(word)));
    }

    /** A line without a unit counts pieces ("2 Eier"). */
    public Optional<NutritionDataset.Unit> resolveLineUnit(String word) {
        return word == null || word.isBlank() ? unit(PIECE) : resolve(word);
    }

    public boolean isKnownUnit(String word) {
        return resolve(word).isPresent();
    }

    public Optional<NutritionDataset.Unit> unit(String key) {
        return Optional.ofNullable(unitsByKey.get(key));
    }
}
