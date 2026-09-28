package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFoodName;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;
import com.sterul.opencookbookapiserver.services.shopping.placement.ItemPlacement;

/**
 * Something to tap when adding to a list, food or not.
 *
 * @param names per language, the one to show first; the others are what a search matches
 */
public record ShoppingTileResponse(String key, Aisle aisle, String icon, Map<String, List<String>> names) {

    public static ShoppingTileResponse of(CatalogueFood food) {
        var names = food.getNames().stream()
                .sorted(Comparator.comparing(CatalogueFoodName::isDisplay).reversed())
                .map(name -> Map.entry(name.getLanguageIsoCode(), name.getName()));
        return of(food.getCatalogueKey(), ItemPlacement.of(food).orElse(ItemPlacement.UNKNOWN), names);
    }

    public static ShoppingTileResponse of(CatalogueDataset.NonFoodItem item) {
        return of(item.key(), ItemPlacement.of(item),
                item.names().stream().map(name -> Map.entry(name.language(), name.name())));
    }

    private static ShoppingTileResponse of(String key, ItemPlacement placement, Stream<Map.Entry<String, String>> names) {
        return new ShoppingTileResponse(key, placement.aisle(), placement.icon(), byLanguage(names));
    }

    private static Map<String, List<String>> byLanguage(Stream<Map.Entry<String, String>> names) {
        return names.collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
}
