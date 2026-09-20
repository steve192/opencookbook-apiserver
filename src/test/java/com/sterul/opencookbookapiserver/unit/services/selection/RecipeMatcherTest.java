package com.sterul.opencookbookapiserver.unit.services.selection;

import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.food;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.ingredient;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.linked;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.recipe;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.variantOf;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.withNamelessLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;
import com.sterul.opencookbookapiserver.services.selection.RecipeMatcher;

/**
 * What counts as "this recipe uses that ingredient". Matching through the catalogue is the whole
 * reason the feature finds anything: one cook easily holds "Tomate" and "Tomaten" as two rows,
 * and a recipe written with a prepared form of a food is still a recipe with that food in it.
 */
class RecipeMatcherTest {

    private final RecipeMatcher cut = new RecipeMatcher();

    private static final CatalogueFood TOMATO = food(100, "bls-G123456");
    private static final CatalogueFood TROUT = food(200, "bls-T422100");
    private static final CatalogueFood FRIED_TROUT = variantOf(201, "bls-T420162", TROUT);

    @Test
    void twoIngredientsOfTheSameFoodAreTheSameThing() {
        var tomato = new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId()));
        var soup = recipe(10, "Tomatensuppe", linked(2, "Tomaten", TOMATO));

        assertTrue(cut.match(soup, List.of(tomato)).hasAll());
    }

    @Test
    void aFoodMatchesItsPreparedForms() {
        var trout = new MatchTarget(1L, "Forelle", Set.of(TROUT.getId(), FRIED_TROUT.getId()));
        var dish = recipe(10, "Forelle Müllerin", linked(2, "Forelle gebraten", FRIED_TROUT));

        assertTrue(cut.match(dish, List.of(trout)).hasAll());
    }

    @Test
    void anUnlinkedSelectionOnlyMatchesItsOwnIngredient() {
        var ownSpice = new MatchTarget(1L, "Omas Gewürz", Set.of());

        assertTrue(cut.match(recipe(10, "Gulasch", ingredient(1, "Omas Gewürz")), List.of(ownSpice)).hasAll());
        assertFalse(cut.match(recipe(11, "Suppe", ingredient(2, "Omas Gewürz")), List.of(ownSpice)).hasAny());
    }

    @Test
    void whatTheRecipeLacksIsReported() {
        var tomato = new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId()));
        var trout = new MatchTarget(2L, "Forelle", Set.of(TROUT.getId()));

        var match = cut.match(recipe(10, "Tomatensuppe", linked(3, "Tomate", TOMATO)), List.of(tomato, trout));

        assertEquals(List.of(tomato), match.matched());
        assertEquals(List.of(trout), match.missing());
        assertEquals(0.5, match.targetCoverage(), 0.001);
    }

    @Test
    void lineCoverageSaysHowMuchOfTheRecipeTheCookAlreadyHas() {
        var tomato = new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId()));
        var soup = recipe(10, "Tomatensuppe", linked(2, "Tomate", TOMATO), ingredient(3, "Sahne"),
                ingredient(4, "Basilikum"));

        assertEquals(1 / 3d, cut.match(soup, List.of(tomato)).lineCoverage(), 0.001);
    }

    @Test
    void aLineNamingNoIngredientIsNotCounted() {
        var tomato = new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId()));
        var soup = withNamelessLine(recipe(10, "Tomatensuppe", linked(2, "Tomate", TOMATO)));

        var match = cut.match(soup, List.of(tomato));

        assertEquals(1, match.totalLines());
        assertEquals(1, match.lineCoverage(), 0.001);
    }
}
