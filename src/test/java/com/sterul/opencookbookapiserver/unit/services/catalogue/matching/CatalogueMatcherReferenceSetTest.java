package com.sterul.opencookbookapiserver.unit.services.catalogue.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;
import com.sterul.opencookbookapiserver.unit.services.catalogue.ShippedCatalogueDataset;

/** Regression floors on the reference gold set; they only move up. */
class CatalogueMatcherReferenceSetTest {

    // Measured with matcher version 3 on the reference set of 618 names: 0.993 and 0.979.
    private static final double SILENT_PRECISION_FLOOR = 0.99;
    private static final double COVERAGE_FLOOR = 0.97;

    private static final List<GoldNames.GoldName> REFERENCE_SET = GoldNames.read(GoldNames.REFERENCE_SET);

    @Test
    void everyExpectedFoodIsInTheCatalogue() {
        var keys = ShippedCatalogueDataset.READER.catalogue().foods().stream()
                .map(CatalogueDataset.Food::key).collect(Collectors.toSet());
        var unknown = REFERENCE_SET.stream()
                .flatMap(name -> name.expected().stream())
                .filter(key -> !keys.contains(key))
                .toList();
        assertEquals(List.of(), unknown, "expected foods the catalogue does not have");
    }

    @Test
    void silentLinksAreRightAndEnoughNamesAreLinked() {
        var evaluation = MatcherEvaluation.of(ShippedCatalogueMatcher.MATCHER, REFERENCE_SET);
        var report = evaluation + "\n" + String.join("\n", evaluation.misses());

        assertTrue(evaluation.silentPrecision() >= SILENT_PRECISION_FLOOR, report);
        assertTrue(evaluation.coverage() >= COVERAGE_FLOOR, report);
        System.out.println("Reference set: " + report);
    }
}
