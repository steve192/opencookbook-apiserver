package com.sterul.opencookbookapiserver.repositories.projections;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface IngredientUseCount {

    Long getIngredientId();

    long getCount();

    /** By ingredient id. */
    static Map<Long, Long> asMap(List<? extends IngredientUseCount> counts) {
        return counts.stream().collect(Collectors.toMap(IngredientUseCount::getIngredientId, IngredientUseCount::getCount));
    }
}
