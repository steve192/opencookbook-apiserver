package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.IngredientMatcher;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

/**
 * Exercises the real fuzzy matching. {@link IngredientMatcher} is a pure component,
 * so it needs neither Spring nor mocks to be tested.
 */
class IngredientMatcherTest {

    private final IngredientMatcher cut = new IngredientMatcher();
    private final AtomicLong ids = new AtomicLong();

    private Ingredient ingredientNamed(String name) {
        return Ingredient.builder().id(ids.incrementAndGet()).name(name).build();
    }

    @ParameterizedTest(name = "\"{1}\" matches stored ingredient \"{0}\"")
    @CsvSource({
            "Bratwürste, Bratwürstchen",
            "Lauchzwiebel(n), Lauchzwiebel",
            "Salz und Pfeffer, Salz & Pfeffer",
            "Salz, Salz*",
    })
    void similarNamesAreMatched(String storedName, String searchedName) throws ElementNotFound {
        var stored = ingredientNamed(storedName);

        assertEquals(stored, cut.findIngredientbySimilarName(List.of(stored), searchedName));
    }

    @ParameterizedTest(name = "\"{1}\" does not match stored ingredient \"{0}\"")
    @CsvSource({
            "Brötchen, Brokkoli",
            "'Lachs (Sashimi-Qualität)', Lachs",
            "'Paprikapulver , scharf', Paprikapulver",
    })
    void unsimilarNamesAreNotMatched(String storedName, String searchedName) {
        var stored = ingredientNamed(storedName);

        assertThrows(ElementNotFound.class, () -> cut.findIngredientbySimilarName(List.of(stored), searchedName));
    }

    @Test
    void bestOfSeveralCandidatesIsReturned() throws ElementNotFound {
        var brokkoli = ingredientNamed("Brokkoli");
        var broetchen = ingredientNamed("Brötchen");

        assertEquals(broetchen, cut.findIngredientbySimilarName(List.of(brokkoli, broetchen), "Brötchen"));
    }

    @Test
    void emptyCandidateListIsRejected() {
        assertThrows(ElementNotFound.class, () -> cut.findIngredientbySimilarName(List.of(), "Salz"));
    }
}
