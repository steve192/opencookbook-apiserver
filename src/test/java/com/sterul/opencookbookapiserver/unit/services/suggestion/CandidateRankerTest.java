package com.sterul.opencookbookapiserver.unit.services.suggestion;

import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.food;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.ingredient;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.linked;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;
import com.sterul.opencookbookapiserver.services.selection.RecipeMatcher;
import com.sterul.opencookbookapiserver.services.suggestion.CandidateRanker;
import com.sterul.opencookbookapiserver.services.suggestion.MatchMode;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;
import com.sterul.opencookbookapiserver.services.suggestion.scoring.IngredientCoverageScorer;
import com.sterul.opencookbookapiserver.services.suggestion.scoring.JitterScorer;
import com.sterul.opencookbookapiserver.services.suggestion.scoring.RecipeCoverageScorer;

/** How the ranking orders what the pool let through, with nutrition switched off. */
class CandidateRankerTest {

    private static final CatalogueFood TOMATO = food(100, "bls-G123456");
    private static final CatalogueFood FETA = food(101, "bls-M234567");

    private final RecipeMatcher matcher = new RecipeMatcher();
    private final CandidateRanker cut = new CandidateRanker(
            List.of(new IngredientCoverageScorer(), new RecipeCoverageScorer(), new JitterScorer()),
            List.of(), Optional.empty());

    private static final List<MatchTarget> TARGETS = List.of(
            new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId())),
            new MatchTarget(2L, "Feta", Set.of(FETA.getId())));

    private static SuggestionCriteria criteria(long seed) {
        return new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(1L, 2L), null, null, null, null, null, 10, seed);
    }

    private List<String> rank(long seed, Recipe... recipes) {
        var candidates = List.of(recipes).stream()
                .map(recipe -> new SuggestionCandidate(recipe, matcher.match(recipe, TARGETS)))
                .toList();
        return cut.rank(candidates, criteria(seed)).stream().map(suggestion -> suggestion.recipe().getTitle()).toList();
    }

    @Test
    void usingMoreOfTheWantedIngredientsRanksHigher() {
        var both = recipe(1, "Tomate-Feta", linked(10, "Tomate", TOMATO), linked(11, "Feta", FETA));
        var one = recipe(2, "Tomatensuppe", linked(12, "Tomate", TOMATO));

        assertEquals(List.of("Tomate-Feta", "Tomatensuppe"), rank(1L, one, both));
    }

    /**
     * Between two recipes using the same wanted ingredients, the one needing less else is closer
     * to cookable - the only approximation of "nothing more to buy" available without knowing
     * which staples a kitchen keeps.
     */
    @Test
    void amongEqualMatchesTheRecipeNeedingLessElseRanksHigher() {
        var lean = recipe(1, "Caprese", linked(10, "Tomate", TOMATO), ingredient(11, "Basilikum"));
        var elaborate = recipe(2, "Auflauf", linked(12, "Tomate", TOMATO), ingredient(13, "Sahne"),
                ingredient(14, "Mehl"), ingredient(15, "Butter"), ingredient(16, "Muskat"));

        assertEquals(List.of("Caprese", "Auflauf"), rank(1L, elaborate, lean));
    }

    @Test
    void oneSeedAlwaysGivesOneOrder() {
        var first = recipe(1, "Erstes", linked(10, "Tomate", TOMATO));
        var second = recipe(2, "Zweites", linked(11, "Tomate", TOMATO));

        assertEquals(rank(42L, first, second), rank(42L, second, first));
    }

    @Test
    void anotherSeedShufflesEquallyGoodRecipes() {
        var recipes = new Recipe[20];
        for (var index = 0; index < recipes.length; index++) {
            recipes[index] = recipe(index + 1, "Rezept " + index, linked(100 + index, "Tomate", TOMATO));
        }

        assertNotEquals(rank(1L, recipes), rank(2L, recipes));
    }
}
