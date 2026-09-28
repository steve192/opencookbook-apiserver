package com.sterul.opencookbookapiserver.services.catalogue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

/** Units and their words in all shipped languages ("EL", "tbsp"). */
@Component
public class UnitLexicon {

    private static final String PIECE = "piece";

    private final Map<String, CatalogueDataset.Unit> unitsByKey;
    private final Map<String, CatalogueDataset.Unit> unitsByWord;

    public UnitLexicon(CatalogueDatasetReader reader) {
        unitsByKey = reader.units().units().stream()
                .collect(Collectors.toUnmodifiableMap(CatalogueDataset.Unit::key, Function.identity()));
        var byWord = new HashMap<String, CatalogueDataset.Unit>();
        for (var lexicon : reader.lexicons().lexicons()) {
            lexicon.units().forEach((key, words) -> words.forEach(word -> byWord.put(IngredientNames.normalise(word), unitsByKey.get(key))));
        }
        unitsByWord = Map.copyOf(byWord);
    }

    public Optional<CatalogueDataset.Unit> resolve(String word) {
        return word == null ? Optional.empty() : Optional.ofNullable(unitsByWord.get(IngredientNames.normalise(word)));
    }

    /** A line without a unit counts pieces ("2 Eier"). */
    public Optional<CatalogueDataset.Unit> resolveLineUnit(String word) {
        return word == null || word.isBlank() ? unit(PIECE) : resolve(word);
    }

    public boolean isKnownUnit(String word) {
        return resolve(word).isPresent();
    }

    /** Every unit word, normalised. */
    public Set<String> words() {
        return unitsByWord.keySet();
    }

    public Optional<CatalogueDataset.Unit> unit(String key) {
        return Optional.ofNullable(unitsByKey.get(key));
    }
}
