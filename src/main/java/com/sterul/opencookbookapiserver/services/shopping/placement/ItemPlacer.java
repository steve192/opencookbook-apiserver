package com.sterul.opencookbookapiserver.services.shopping.placement;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Where an item is sold: the first source that knows, otherwise "other". */
@Component
public class ItemPlacer {

    private final List<PlacementSource> sources;

    ItemPlacer(List<PlacementSource> sources) {
        this.sources = sources;
    }

    public ItemPlacement place(PlacementQuery query) {
        return sources.stream()
                .map(source -> source.place(query))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(ItemPlacement.UNKNOWN);
    }
}
