package com.sterul.opencookbookapiserver.unit.services.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

/** What importer and calculator rely on in the shipped dataset; catches generator regressions. */
class ShippedDatasetIntegrityTest {

    private static final NutritionDatasetReader READER = ShippedNutritionDataset.READER;
    private static final NutritionDataset.Manifest MANIFEST = READER.manifest();
    private static final List<NutritionDataset.Food> FOODS = READER.catalogue().foods();
    private static final Map<String, NutritionDataset.Food> BY_KEY = FOODS.stream()
            .collect(Collectors.toMap(NutritionDataset.Food::key, Function.identity(), (first, second) -> first));
    private static final Map<String, NutritionDataset.Unit> UNITS = READER.units().units().stream()
            .collect(Collectors.toMap(NutritionDataset.Unit::key, Function.identity()));
    private static final Set<String> STATES = READER.states().states().stream()
            .map(NutritionDataset.State::key).collect(Collectors.toSet());

    /** Grams of fat, carbohydrates, protein and fibre per 100 g; above it a value is misread, not dry. */
    private static final float MOST_MACRONUTRIENTS = 110;

    @Test
    void theFilesAreTheOnesTheManifestDescribes() {
        assertEquals(MANIFEST.checksum(), READER.computeChecksum());
        assertEquals(MANIFEST.foods(), FOODS.size());
    }

    @Test
    void everyFoodHasItsOwnKeyDerivedFromItsSource() {
        assertEquals(FOODS.size(), BY_KEY.size());
        assertNone(FOODS, food -> !food.key().equals(food.source().type().toLowerCase(Locale.ROOT) + "-" + food.source().code()),
                "foods whose key is not derived from their source");
    }

    @Test
    void aVariantBelongsToABaseAndLeavesTheNamesToIt() {
        var variants = FOODS.stream().filter(food -> food.variantOf() != null).toList();
        assertNone(variants, food -> !BY_KEY.containsKey(food.variantOf()), "variants of a food not in the dataset");
        assertNone(variants, food -> BY_KEY.get(food.variantOf()).variantOf() != null, "variants of a variant");
        assertNone(variants, food -> !food.names().isEmpty(), "variants with names of their own");
    }

    @Test
    void everyBaseHasOneDisplayNamePerShippedLanguage() {
        var bases = FOODS.stream().filter(food -> food.variantOf() == null).toList();
        for (var language : MANIFEST.languages()) {
            assertNone(bases, food -> food.names().stream().filter(name -> name.display() && name.language().equals(language)).count() != 1,
                    "bases without exactly one display name in " + language);
        }
    }

    @Test
    void aNameBelongsToOneFoodWhateverItsCapitalisation() {
        var seen = new HashSet<String>();
        var taken = FOODS.stream().flatMap(food -> food.names().stream())
                .map(name -> name.language() + " " + name.name().toLowerCase(Locale.ROOT))
                .filter(name -> !seen.add(name))
                .toList();
        assertEquals(List.of(), taken);
    }

    @Test
    void nutrientsArePlausible() {
        assertNone(FOODS, food -> food.nutrients().energyKcal() == null || food.nutrients().energyKj() == null,
                "foods without energy");
        assertNone(FOODS, food -> food.nutrients().energyKcal() < 0 || food.nutrients().energyKcal() > 900,
                "foods with energy beyond pure fat");
        // Rounded to a tenth, so the ratio only means something above a few kilocalories.
        assertNone(FOODS, food -> Math.abs(food.nutrients().energyKj() - 4.184f * food.nutrients().energyKcal())
                > Math.max(1f, 0.05f * food.nutrients().energyKj()), "foods whose kJ and kcal disagree");
        assertNone(FOODS, food -> values(food.nutrients()).anyMatch(value -> value < 0), "foods with negative nutrients");
        assertNone(FOODS, food -> exceeds(food.nutrients().saturatedFat(), food.nutrients().fat()), "foods with more saturated fat than fat");
        assertNone(FOODS, food -> exceeds(food.nutrients().sugar(), food.nutrients().carbohydrates()), "foods with more sugar than carbohydrates");
        assertNone(FOODS, food -> Stream.of(food.nutrients().fat(), food.nutrients().carbohydrates(), food.nutrients().protein(),
                food.nutrients().fibre()).filter(Objects::nonNull).reduce(0f, Float::sum) > MOST_MACRONUTRIENTS,
                "foods with more than " + MOST_MACRONUTRIENTS + " g of macronutrients per 100 g");
    }

    @Test
    void amountsAreInKnownCountUnitsAndDensitiesArePlausible() {
        assertNone(FOODS, food -> food.portions().stream().anyMatch(portion -> !UNITS.containsKey(portion.unit())
                || UNITS.get(portion.unit()).kind() != NutritionDataset.UnitKind.COUNT), "foods with portions in no count unit");
        assertNone(FOODS, food -> food.portions().stream().anyMatch(portion -> portion.grams() <= 0), "foods with portions weighing nothing");
        assertNone(FOODS, food -> food.densityGPerMl() != null && (food.densityGPerMl() < 0.1f || food.densityGPerMl() > 2.5f),
                "foods with implausible densities");
    }

    @Test
    void referenceDataRefersOnlyToWhatExists() {
        assertNone(FOODS, food -> !STATES.containsAll(food.states()), "foods in unknown states");
        assertNone(UNITS.values(), unit -> unit.sizeOf() != null && !UNITS.containsKey(unit.sizeOf()), "units the size of unknown units");
        for (var lexicon : READER.lexicons().lexicons()) {
            assertEquals(Set.of(), difference(lexicon.units().keySet(), UNITS.keySet()), lexicon.language() + " words for unknown units");
            assertEquals(Set.of(), difference(lexicon.states().keySet(), STATES), lexicon.language() + " words for unknown states");
        }
        assertEquals(Set.copyOf(MANIFEST.languages()),
                READER.lexicons().lexicons().stream().map(NutritionDataset.Lexicon::language).collect(Collectors.toSet()));
    }

    @Test
    void everySourceIsAttributedAsItsLicenseRequires() {
        var attributed = MANIFEST.attributions().stream().map(NutritionDataset.Attribution::source).collect(Collectors.toSet());
        assertTrue(attributed.containsAll(FOODS.stream().map(food -> food.source().type()).collect(Collectors.toSet())));
        var bls = MANIFEST.attributions().stream().filter(attribution -> attribution.source().equals("BLS")).findFirst().orElseThrow();
        assertTrue(bls.text().contains("Max Rubner-Institut"), bls.text());
        assertEquals("CC BY 4.0", bls.license());
        assertEquals("https://creativecommons.org/licenses/by/4.0/", bls.licenseUrl());
    }

    private static <T> void assertNone(Collection<T> items, Predicate<T> violation, String description) {
        var violating = items.stream().filter(violation).limit(10).toList();
        assertEquals(List.of(), violating, description);
    }

    private static Stream<Float> values(NutritionDataset.Nutrients nutrients) {
        return Stream.of(nutrients.energyKcal(), nutrients.energyKj(), nutrients.fat(), nutrients.saturatedFat(),
                nutrients.carbohydrates(), nutrients.sugar(), nutrients.fibre(), nutrients.protein(), nutrients.salt())
                .filter(Objects::nonNull);
    }

    private static boolean exceeds(Float part, Float whole) {
        return part != null && whole != null && part > whole;
    }

    private static Set<String> difference(Set<String> set, Set<String> without) {
        return set.stream().filter(key -> !without.contains(key)).collect(Collectors.toSet());
    }
}
