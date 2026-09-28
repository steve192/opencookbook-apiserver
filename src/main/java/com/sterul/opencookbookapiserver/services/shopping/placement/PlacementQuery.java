package com.sterul.opencookbookapiserver.services.shopping.placement;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;

/**
 * @param language the asker's, tried first; null if unknown
 * @param linkedFood what the name's ingredient is linked to; null for a typed name
 */
public record PlacementQuery(String name, String language, CatalogueFood linkedFood) {

    public static PlacementQuery typed(String name, String language) {
        return new PlacementQuery(name, language, null);
    }
}
