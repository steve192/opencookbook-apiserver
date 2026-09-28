package com.sterul.opencookbookapiserver.services.shopping.preview;

import java.util.Optional;
import java.util.Set;

import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;

/** What amounts add up in. Spoons and cups are volumes too, but nobody shops for "45 ml oil". */
final class MergeUnits {

    static final String GRAMS = "g";
    static final String MILLILITRES = "ml";

    private static final Set<String> METRIC_VOLUMES = Set.of("millilitre", "centilitre", "decilitre", "litre");

    private MergeUnits() {
    }

    record MergeUnit(String unit, float factor) {
    }

    static MergeUnit of(Optional<CatalogueDataset.Unit> resolved, String written) {
        return resolved.map(unit -> switch (unit.kind()) {
            case MASS -> new MergeUnit(GRAMS, unit.grams());
            case VOLUME -> METRIC_VOLUMES.contains(unit.key())
                    ? new MergeUnit(MILLILITRES, unit.millilitres())
                    : new MergeUnit(unit.key(), 1);
            default -> new MergeUnit(unit.key(), 1);
        }).orElseGet(() -> new MergeUnit(written == null ? "" : IngredientNames.normalise(written), 1));
    }
}
