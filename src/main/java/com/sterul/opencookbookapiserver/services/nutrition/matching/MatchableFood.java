package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/**
 * A catalogue food as the matcher sees it, from the database or the shipped dataset.
 *
 * @param variantOf key of the base food; null for a base
 * @param names     empty for variants, which are found through their base
 */
public record MatchableFood(String key, String variantOf, Set<String> states, List<Name> names) {

    public MatchableFood {
        states = Set.copyOf(states);
        names = List.copyOf(names);
    }

    public record Name(String language, String name) {
    }

    public static MatchableFood of(CatalogueFood food) {
        var variantOf = food.getVariantOf();
        return new MatchableFood(food.getCatalogueKey(), variantOf == null ? null : variantOf.getCatalogueKey(),
                food.getStates(),
                food.getNames().stream().map(name -> new Name(name.getLanguageIsoCode(), name.getName())).toList());
    }

    public static MatchableFood of(NutritionDataset.Food food) {
        return new MatchableFood(food.key(), food.variantOf(), Set.copyOf(food.states()),
                food.names().stream().map(name -> new Name(name.language(), name.name())).toList());
    }
}
