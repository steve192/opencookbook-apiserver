package com.sterul.opencookbookapiserver.unit.services.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.selection.FuzzyTitleSearch;

/** Every expectation here is what the app's npm package fuzzy answers for the same input. */
class FuzzyTitleSearchTest {

    private static final List<String> TITLES = List.of("Kartoffelgratin", "Linsensuppe", "Lasagne al forno");

    private static List<String> search(String input, List<String> titles) {
        return FuzzyTitleSearch.filter(input, titles, Function.identity());
    }

    @Test
    void findsTheInputInsideAWordWhileItIsStillBeingTyped() {
        assertEquals(List.of("Kartoffelgratin"), search("gratin", TITLES));
        assertEquals(List.of("Lasagne al forno"), search("lasa", TITLES));
    }

    @Test
    void forgivesALeftOutLetterAndCase() {
        assertEquals(List.of("Lasagne al forno"), search("lasgne", TITLES));
        assertEquals(List.of("Lasagne al forno"), search("LASA", TITLES));
    }

    @Test
    void leavesOutTitlesThatDoNotHoldEveryCharacterInOrder() {
        assertEquals(List.of(), search("xyz", TITLES));
    }

    @Test
    void putsConsecutiveMatchesFirst() {
        assertEquals(List.of("Linsensuppe", "Spinat und Pute mit Pesto"),
                search("suppe", List.of("Spinat und Pute mit Pesto", "Linsensuppe")));
    }

    @Test
    void putsTheWholeTitleFirst() {
        assertEquals(List.of("Suppe", "Linsensuppe"), search("Suppe", List.of("Linsensuppe", "Suppe")));
    }

    @Test
    void keepsTheOrderOfEqualMatches() {
        assertEquals(List.of("Apfel", "Banane"), search("a", List.of("Birne", "Apfel", "Banane")));
    }
}
