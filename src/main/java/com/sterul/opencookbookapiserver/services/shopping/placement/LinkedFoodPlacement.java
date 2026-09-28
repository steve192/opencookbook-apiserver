package com.sterul.opencookbookapiserver.services.shopping.placement;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
class LinkedFoodPlacement implements PlacementSource {

    @Override
    public Optional<ItemPlacement> place(PlacementQuery query) {
        return Optional.ofNullable(query.linkedFood()).flatMap(ItemPlacement::of);
    }
}
