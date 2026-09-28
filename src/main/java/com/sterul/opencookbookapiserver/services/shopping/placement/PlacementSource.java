package com.sterul.opencookbookapiserver.services.shopping.placement;

import java.util.Optional;

/** One way of knowing where an item is sold; asked in {@code @Order} until one knows. */
interface PlacementSource {

    Optional<ItemPlacement> place(PlacementQuery query);
}
