package com.sterul.opencookbookapiserver.unit.services.catalogue.matching;

import java.util.Set;

import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.catalogue.matching.MatchableFood;
import com.sterul.opencookbookapiserver.services.catalogue.matching.MatcherWeights;
import com.sterul.opencookbookapiserver.unit.services.catalogue.ShippedCatalogueDataset;

/** Matchers over the catalogue shipped in the jar, for tests that run without Spring and a database. */
public final class ShippedCatalogueMatcher {

    private ShippedCatalogueMatcher() {
    }

    /** With the shipped weights. Building the index takes a few seconds, so tests share this one. */
    public static final CatalogueMatcher MATCHER = withWeights(MatcherWeights.shipped(ShippedCatalogueDataset.READER));

    public static CatalogueMatcher withWeights(MatcherWeights weights) {
        var matcher = new CatalogueMatcher(ShippedCatalogueDataset.READER, weights);
        matcher.rebuild(ShippedCatalogueDataset.READER.catalogue().foods().stream().map(ShippedCatalogueMatcher::matchable).toList());
        return matcher;
    }

    private static MatchableFood matchable(CatalogueDataset.Food food) {
        return new MatchableFood(food.key(), food.variantOf(), Set.copyOf(food.states()),
                food.names().stream().map(name -> new MatchableFood.Name(name.language(), name.name())).toList());
    }
}
