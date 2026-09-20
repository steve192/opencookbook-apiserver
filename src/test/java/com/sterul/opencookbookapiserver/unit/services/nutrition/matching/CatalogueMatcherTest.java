package com.sterul.opencookbookapiserver.unit.services.nutrition.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.matching.ConfidenceBand;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchableFood;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;

/** A small catalogue with the shipped lexicons and weights. */
class CatalogueMatcherTest {

    private static final CatalogueMatcher MATCHER = new CatalogueMatcher(ShippedNutritionDataset.READER);

    @BeforeAll
    static void catalogue() {
        MATCHER.rebuild(List.of(
                food("potato", "Kartoffel", "Potato"),
                variant("potato-boiled", "potato", "BOILED"),
                food("tomato", "Tomate", "Tomato"),
                food("tomato-dried", Set.of("DRIED"), "Tomate getrocknet", "Tomato dried"),
                food("peanut", "Erdnuss", "Peanut"),
                food("butter", "Butter", "Butter"),
                food("garlic", "Knoblauch", "Garlic"),
                food("pasta", "Nudeln", "Pasta"),
                food("glass-noodles", "Glasnudeln", "Glass noodles"),
                food("salt", "Salz", "Salt"),
                food("almond", "Mandel", "Almond"),
                food("beer", "Bier", "Beer"),
                food("almond-chopped", "Mandel gehackt", "Almond chopped"),
                food("pepper", "Pfeffer", "Pepper"),
                food("bell-pepper", Set.of(), new MatchableFood.Name("de", "Paprika")),
                food("paprika-powder", Set.of(), new MatchableFood.Name("en", "Paprika"))));
    }

    @Test
    void aPluralFindsItsFood() {
        assertEquals("potato", silentlyLinked("Kartoffeln"));
    }

    @Test
    void wordsThatNameNothingKnownDoNotStopAMatch() {
        assertEquals("potato", linked("mehligkochende Kartoffeln").orElseThrow().foodKey());
    }

    @Test
    void aStateWordChoosesTheVariantInThatState() {
        assertEquals("potato-boiled", silentlyLinked("gekochte Kartoffeln"));
    }

    @Test
    void aProcessedFoodIsChosenOnlyWhenTheNameSaysSo() {
        assertEquals("tomato-dried", silentlyLinked("getrocknete Tomaten"));
        assertEquals("tomato", silentlyLinked("Tomaten"));
        assertEquals("tomato-dried", silentlyLinked("dried tomatoes"));
    }

    @Test
    void aCompoundOfTwoFoodsIsNotLinkedSilentlyToEitherOfThem() {
        // Often a kind of one of them (Orangenmarmelade), sometimes something else entirely (Erdnussbutter).
        var candidates = MATCHER.rank("Erdnussbutter", "de", 2);

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().noneMatch(candidate -> candidate.band() == ConfidenceBand.SILENT), candidates.toString());
    }

    @Test
    void aUnitInsideATypedWordIsNoPartOfTheName() {
        assertEquals("garlic", silentlyLinked("Knoblauchzehen"));
    }

    @Test
    void aCompoundTheCatalogueKnowsIsTheFoodOfThatName() {
        assertEquals("glass-noodles", silentlyLinked("Glasnudeln"));
    }

    @Test
    void aTypoIsForgivenButNotSilently() {
        var candidate = linked("Kartofeln").orElseThrow();

        assertEquals("potato", candidate.foodKey());
        assertTrue(candidate.confidence() < 1);
    }

    @Test
    void theLanguageANameIsWrittenInDecidesBetweenFoodsOfTheSameName() {
        assertEquals("bell-pepper", MATCHER.rank("Paprika", "de", 1).get(0).foodKey());
        assertEquals("paprika-powder", MATCHER.rank("Paprika", "en", 1).get(0).foodKey());
    }

    @Test
    void aNameOfTwoFoodsIsNotLinkedSilently() {
        assertNotEquals(ConfidenceBand.SILENT, linked("Salz und Pfeffer").map(MatchCandidate::band).orElse(ConfidenceBand.NONE));
    }

    @Test
    void aDescriptionIsNotHeldAgainstAFoodWithoutIt() {
        assertEquals("garlic", silentlyLinked("Knoblauch, fein gehackt"));
        assertEquals("butter", silentlyLinked("Butter für die Form"));
    }

    @Test
    void aFoodWhoseNameSharesTheDescriptionStaysAhead() {
        assertEquals("almond-chopped", silentlyLinked("gehackte Mandeln"));
        assertEquals("almond", silentlyLinked("Mandeln"));
    }

    @Test
    void aWordMeetsANameOnlyWhereItIsTheSameWordInThatNamesLanguage() {
        // German "Beeren" stems to "beer", which is English for Bier
        assertNotEquals("beer", linked("Beeren").map(MatchCandidate::foodKey).orElse(""));
        assertEquals("beer", silentlyLinked("Bier"));
        assertEquals("potato", silentlyLinked("potatoes"));
    }

    @Test
    void aDescriptionAloneNamesNothing() {
        assertEquals(Optional.empty(), linked("fein gehackt"));
    }

    @Test
    void aNameOfNothingButStopwordsAndUnitsFindsNothing() {
        assertEquals(List.of(), MATCHER.rank("etwas von der", "de", 3));
    }

    @Test
    void aMatcherWithoutAnIndexIsNotReadyAndFindsNothing() {
        var unbuilt = new CatalogueMatcher(ShippedNutritionDataset.READER);

        assertFalse(unbuilt.isReady());
        assertEquals(List.of(), unbuilt.rank("Kartoffeln", "de", 3));
    }

    private static String silentlyLinked(String typed) {
        var candidate = linked(typed).orElseThrow(() -> new AssertionError(typed + " is not linked: " + MATCHER.rank(typed, "de", 3)));
        assertEquals(ConfidenceBand.SILENT, candidate.band(), typed + ": " + candidate);
        return candidate.foodKey();
    }

    private static Optional<MatchCandidate> linked(String typed) {
        return MATCHER.match(typed, "de");
    }

    private static MatchableFood food(String key, String german, String english) {
        return food(key, Set.of(), german, english);
    }

    private static MatchableFood food(String key, Set<String> states, String german, String english) {
        return food(key, states, new MatchableFood.Name("de", german), new MatchableFood.Name("en", english));
    }

    private static MatchableFood food(String key, Set<String> states, MatchableFood.Name... names) {
        return new MatchableFood(key, null, states, Arrays.asList(names));
    }

    private static MatchableFood variant(String key, String base, String state) {
        return new MatchableFood(key, base, Set.of(state), List.of());
    }
}
