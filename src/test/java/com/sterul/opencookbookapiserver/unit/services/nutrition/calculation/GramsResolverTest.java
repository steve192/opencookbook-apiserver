package com.sterul.opencookbookapiserver.unit.services.nutrition.calculation;

import static com.sterul.opencookbookapiserver.unit.services.nutrition.calculation.Foods.estimatedPortion;
import static com.sterul.opencookbookapiserver.unit.services.nutrition.calculation.Foods.food;
import static com.sterul.opencookbookapiserver.unit.services.nutrition.calculation.Foods.portion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.AmountInGrams;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.GramsResolver;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineFlag;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineStatus;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;

class GramsResolverTest {

    private final GramsResolver resolver = new GramsResolver(ShippedNutritionDataset.UNIT_LEXICON);

    @Test
    void massUnitsAreConvertedByTheirFactor() {
        assertGrams(500, Set.of(), resolver.resolve(500f, "g", food("flour", 350), Map.of()));
        assertGrams(1500, Set.of(), resolver.resolve(1.5f, "kg", food("flour", 350), Map.of()));
    }

    @Test
    void aVolumeWeighsWhatTheFoodsDensitySays() {
        var oil = food("oil", 900);
        oil.setDensityGPerMl(0.92f);

        assertGrams(2 * 15 * 0.92, Set.of(), resolver.resolve(2f, "EL", oil, Map.of()));
    }

    @Test
    void aVolumeOfAFoodWithoutDensityIsTakenAsWaterAndSaysSo() {
        assertGrams(250, Set.of(LineFlag.VOLUME_WITHOUT_DENSITY), resolver.resolve(250f, "ml", food("milk", 64), Map.of()));
    }

    @Test
    void piecesWeighWhatAPortionOfTheFoodWeighs() {
        assertGrams(120, Set.of(), resolver.resolve(2f, "", food("egg", 135, portion("piece", 60)), Map.of()));
        assertGrams(12, Set.of(), resolver.resolve(3f, "Zehen", food("garlic", 97, portion("clove", 4)), Map.of()));
    }

    @Test
    void aSmallPieceIsAShareOfAnOrdinaryOne() {
        assertGrams(0.7 * 150, Set.of(LineFlag.SIZE_SCALED_PORTION),
                resolver.resolve(1f, "kleine", food("onion", 34, portion("piece", 150)), Map.of()));
    }

    @Test
    void anEstimatedPortionSaysSo() {
        assertGrams(80, Set.of(LineFlag.ESTIMATED_PORTION), resolver.resolve(1f, "Stück", food("roll", 280, estimatedPortion("piece", 80)), Map.of()));
    }

    @Test
    void aVariantWithoutPortionsWeighsWhatItsBaseDoes() {
        var raw = food("potato", 83, portion("piece", 170));
        CatalogueFood boiled = food("potato-boiled", 80);
        boiled.setVariantOf(raw);

        assertGrams(340, Set.of(), resolver.resolve(2f, "Stück", boiled, Map.of()));
    }

    @Test
    void aContainerTheFoodHasNoPortionForHoldsWhatSuchContainersTypicallyDo() {
        assertGrams(2 * 400, Set.of(LineFlag.TYPICAL_CONTAINER_SIZE), resolver.resolve(2f, "Dosen", food("tomatoes", 20), Map.of()));
        assertGrams(0.5 * 400, Set.of(LineFlag.TYPICAL_CONTAINER_SIZE), resolver.resolve(1f, "kl. Dose/n", food("tomatoes", 20), Map.of()));
    }

    @Test
    void aBottleHoldsATypicalVolume() {
        var oil = food("oil", 900);
        oil.setDensityGPerMl(0.92f);

        assertGrams(700 * 0.92, Set.of(LineFlag.TYPICAL_CONTAINER_SIZE), resolver.resolve(1f, "Flasche", oil, Map.of()));
    }

    @Test
    void aFoodsOwnPortionOfAContainerComesFirst() {
        assertGrams(250, Set.of(), resolver.resolve(1f, "Dose", food("kidney beans", 128, portion("can", 250)), Map.of()));
    }

    @Test
    void theOwnersWeightOfAPieceCountsBeforeTheCatalogues() {
        var egg = food("egg", 135, portion("piece", 60));

        var amount = resolver.resolve(2f, "", egg, Map.of("piece", 70f));

        assertGrams(140, Set.of(), amount);
        assertTrue(amount.ownPortion());
    }

    @Test
    void aSmallContainerIsAShareOfTheOwnersWeightOfAnOrdinaryOne() {
        assertGrams(0.5 * 300, Set.of(), resolver.resolve(1f, "kl. Dose/n", food("tomatoes", 20), Map.of("can", 300f)));
    }

    @Test
    void piecesOfAFoodWithoutAPieceWeightCannotBeWeighed() {
        assertEquals(LineStatus.NO_PORTION, resolver.resolve(2f, "Stück", food("flour", 350), Map.of()).status());
    }

    @Test
    void aPinchWeighsALittleByConventionAlsoWithoutAnAmount() {
        assertGrams(0.4, Set.of(LineFlag.PINCH), resolver.resolve(null, "Prise", food("salt", 0), Map.of()));
    }

    @Test
    void vagueAndMissingAmountsHaveNoGrams() {
        assertEquals(LineStatus.NO_AMOUNT, resolver.resolve(null, "etwas", food("oil", 900), Map.of()).status());
        assertEquals(LineStatus.NO_AMOUNT, resolver.resolve(null, "g", food("flour", 350), Map.of()).status());
        assertEquals(LineStatus.NO_AMOUNT, resolver.resolve(null, "", food("parsley", 33), Map.of()).status());
    }

    @Test
    void anUnknownUnitIsSaidToBeUnknown() {
        assertEquals(LineStatus.UNIT_UNKNOWN, resolver.resolve(1f, "Schnapsglas", food("rum", 230), Map.of()).status());
    }

    private static void assertGrams(double grams, Set<LineFlag> flags, AmountInGrams amount) {
        assertEquals(LineStatus.RESOLVED, amount.status());
        assertEquals(grams, amount.grams(), 0.001);
        assertEquals(flags, amount.flags());
    }
}
