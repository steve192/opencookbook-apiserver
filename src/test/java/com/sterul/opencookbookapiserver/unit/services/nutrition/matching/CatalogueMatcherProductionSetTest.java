package com.sterul.opencookbookapiserver.unit.services.nutrition.matching;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Release criteria on the uncommitted production gold set: {@code mvn verify -Pproduction-gold}. */
@Tag("production-gold")
class CatalogueMatcherProductionSetTest {

    private static final double SILENT_PRECISION_REQUIRED = 0.97;
    private static final double COVERAGE_REQUIRED = 0.90;

    @Test
    void theMatcherMeetsTheReleaseCriteria() {
        assumeTrue(Files.exists(GoldNames.PRODUCTION_SET), "no production gold set at " + GoldNames.PRODUCTION_SET);

        var evaluation = MatcherEvaluation.of(ShippedCatalogueMatcher.MATCHER, GoldNames.read(GoldNames.PRODUCTION_SET));
        // Aggregates only: the misses name what users wrote and stay on this machine.
        System.out.println("Production set: " + evaluation);

        assertTrue(evaluation.silentPrecision() >= SILENT_PRECISION_REQUIRED, evaluation.toString());
        assertTrue(evaluation.coverage() >= COVERAGE_REQUIRED, evaluation.toString());
    }
}
