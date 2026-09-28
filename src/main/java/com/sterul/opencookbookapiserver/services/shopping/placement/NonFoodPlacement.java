package com.sterul.opencookbookapiserver.services.shopping.placement;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.NonFoodItems;

/** First, so "Küchenrolle" is never matched to a food. */
@Component
@Order(1)
class NonFoodPlacement implements PlacementSource {

    private final NonFoodItems nonFood;

    NonFoodPlacement(NonFoodItems nonFood) {
        this.nonFood = nonFood;
    }

    @Override
    public Optional<ItemPlacement> place(PlacementQuery query) {
        if (query.linkedFood() != null) {
            return Optional.empty();
        }
        return nonFood.find(query.name(), query.language()).map(ItemPlacement::of);
    }
}
