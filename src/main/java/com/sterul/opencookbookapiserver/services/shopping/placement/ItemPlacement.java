package com.sterul.opencookbookapiserver.services.shopping.placement;

import java.util.Optional;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;

/** @param icon null when only the aisle is known; the app then shows the aisle's icon */
public record ItemPlacement(Aisle aisle, String icon) {

    public static final ItemPlacement UNKNOWN = new ItemPlacement(Aisle.OTHER, null);

    /** Empty for a food nobody placed. */
    public static Optional<ItemPlacement> of(CatalogueFood food) {
        return Optional.ofNullable(food.getAisle()).map(aisle -> new ItemPlacement(aisle, food.shoppingIcon()));
    }

    public static ItemPlacement of(CatalogueDataset.NonFoodItem item) {
        return new ItemPlacement(item.aisle(), item.icon());
    }
}
