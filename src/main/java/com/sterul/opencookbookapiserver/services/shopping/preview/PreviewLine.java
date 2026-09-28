package com.sterul.opencookbookapiserver.services.shopping.preview;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;

/**
 * One ingredient of one meal as the recipe states it; the app scales and merges these.
 *
 * @param mergeUnit what the amount adds up in: "g" or "ml" across metric units, otherwise the unit itself
 * @param mergeFactor turns the amount into mergeUnit ("1 kg" is 1000 g)
 * @param staple whether the viewer usually leaves it out
 */
public record PreviewLine(String name, String nameKey, Float amount, String unit, String mergeUnit,
        float mergeFactor, Aisle aisle, String icon, boolean staple) {
}
