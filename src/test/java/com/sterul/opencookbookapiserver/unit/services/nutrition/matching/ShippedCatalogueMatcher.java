package com.sterul.opencookbookapiserver.unit.services.nutrition.matching;

import java.util.Set;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchableFood;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatcherWeights;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;

/** Matchers over the catalogue shipped in the jar, for tests that run without Spring and a database. */
public final class ShippedCatalogueMatcher {

    private ShippedCatalogueMatcher() {
    }

    /** With the shipped weights. Building the index takes a few seconds, so tests share this one. */
    public static final CatalogueMatcher MATCHER = withWeights(MatcherWeights.shipped(ShippedNutritionDataset.READER));

    public static CatalogueMatcher withWeights(MatcherWeights weights) {
        var matcher = new CatalogueMatcher(ShippedNutritionDataset.READER, weights);
        matcher.rebuild(ShippedNutritionDataset.READER.catalogue().foods().stream().map(ShippedCatalogueMatcher::matchable).toList());
        return matcher;
    }

    private static MatchableFood matchable(NutritionDataset.Food food) {
        return new MatchableFood(food.key(), food.variantOf(), Set.copyOf(food.states()),
                food.names().stream().map(name -> new MatchableFood.Name(name.language(), name.name())).toList());
    }
}
