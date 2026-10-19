package com.sterul.opencookbookapiserver.services.catalogue;

import java.util.List;
import java.util.Map;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;

/**
 * What one feature knows about some catalogue foods, kept in that feature's own table and replaced by each dataset
 * release (docs/catalogue.md). Implemented rather than registered: the import applies every implementation once
 * the release's foods are in, so a new feature never touches the food entity or the import.
 */
public interface CatalogueFacts {

    /**
     * Replaces the feature's facts with the release's, in the import's transaction.
     *
     * @param foods  the release's foods by catalogue key
     * @param report lines for the import report
     */
    void apply(Map<String, CatalogueFood> foods, List<String> report);

    /** A release ships facts for its own foods; any other is skipped, and the report says how many. */
    static String reportLine(String facts, int applied, int shipped) {
        var skipped = shipped - applied;
        return applied + " " + facts + (skipped == 0 ? "" : ", " + skipped + " skipped for foods not in the release");
    }
}
