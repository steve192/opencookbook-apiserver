package com.sterul.opencookbookapiserver.services.catalogue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

/** What a food shop sells that is no food, shipped with the catalogue; no name is also a food's. */
@Component
public class NonFoodItems {

    private final List<CatalogueDataset.NonFoodItem> items;
    private final Map<String, Map<String, CatalogueDataset.NonFoodItem>> byLanguageAndName = new HashMap<>();

    public NonFoodItems(CatalogueDatasetReader reader) {
        items = reader.nonFood().items();
        for (var item : items) {
            item.names().forEach(name -> byLanguageAndName
                    .computeIfAbsent(name.language(), ignored -> new HashMap<>())
                    .put(IngredientNames.normalise(name.name()), item));
        }
    }

    public List<CatalogueDataset.NonFoodItem> items() {
        return items;
    }

    /** @param language tried first; null if unknown */
    public Optional<CatalogueDataset.NonFoodItem> find(String name, String language) {
        var key = IngredientNames.normalise(name);
        var preferred = Stream.ofNullable(byLanguageAndName.get(language));
        var others = byLanguageAndName.entrySet().stream()
                .filter(names -> !names.getKey().equals(language))
                .map(Map.Entry::getValue);
        return Stream.concat(preferred, others).map(names -> names.get(key)).filter(Objects::nonNull).findFirst();
    }
}
