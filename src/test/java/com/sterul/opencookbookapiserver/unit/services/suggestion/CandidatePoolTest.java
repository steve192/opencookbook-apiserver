package com.sterul.opencookbookapiserver.unit.services.suggestion;

import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.food;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.linked;
import static com.sterul.opencookbookapiserver.unit.services.selection.Recipes.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.DishRole;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;
import com.sterul.opencookbookapiserver.services.selection.RecipeMatcher;
import com.sterul.opencookbookapiserver.services.suggestion.CandidatePool;
import com.sterul.opencookbookapiserver.services.suggestion.MatchMode;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/**
 * Which recipes a request could return at all. The rules worth pinning are the ones that decide
 * what is hidden: a filter must narrow by what is known, never by what is merely unrecorded.
 */
class CandidatePoolTest {

    private static final CookpalUser OWNER = new CookpalUser();
    private static final CatalogueFood TOMATO = food(100, "bls-G123456");
    private static final CatalogueFood FETA = food(101, "bls-M234567");

    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
    private final CandidatePool cut = new CandidatePool(recipeRepository, new RecipeMatcher());

    private void cookbook(Recipe... recipes) {
        when(recipeRepository.findByOwnerWithIngredients(any())).thenReturn(Arrays.asList(recipes));
    }

    private List<String> titlesOf(CandidatePool.Pool pool) {
        return pool.hits().stream().map(candidate -> candidate.recipe().getTitle()).toList();
    }

    private static SuggestionCriteria criteria(MatchMode mode, List<Long> ingredientIds) {
        return new SuggestionCriteria(mode, ingredientIds, null, null, null, null, null, 10, 1L);
    }

    /** A sauce or a side is not something to cook for dinner; a recipe nobody marked still is. */
    @Test
    void sidesAndComponentsAreNotOffered() {
        var unmarked = recipe(1, "Ofengemüse");
        var dish = recipe(2, "Gulasch");
        dish.setMealTypes(Set.of(MealType.DINNER));
        var sauce = recipe(3, "Hollandaise");
        sauce.setDishRole(DishRole.COMPONENT);
        var side = recipe(4, "Reis");
        side.setDishRole(DishRole.SIDE);
        cookbook(unmarked, dish, sauce, side);

        var pool = cut.of(OWNER, List.of(), criteria(MatchMode.ANY_RANKED, List.of()));

        assertEquals(List.of("Ofengemüse", "Gulasch"), titlesOf(pool));
    }

    @Test
    void anUnclassifiedRecipeIsNotOfferedToSomebodyWhoAskedForADiet() {
        var unclassified = recipe(1, "Omas Gulasch");
        var vegan = recipe(2, "Ofengemüse");
        vegan.setRecipeType(Diet.VEGAN);
        cookbook(unclassified, vegan);

        var pool = cut.of(OWNER, List.of(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(), null,
                Diet.VEGETARIAN, null, null, null, 10, 1L));

        assertEquals(List.of("Ofengemüse"), titlesOf(pool));
    }

    @Test
    void aDietAllowsEverythingStricterThanItself() {
        var vegan = recipe(1, "Ofengemüse");
        vegan.setRecipeType(Diet.VEGAN);
        var vegetarian = recipe(2, "Käsespätzle");
        vegetarian.setRecipeType(Diet.VEGETARIAN);
        var meat = recipe(3, "Gulasch");
        meat.setRecipeType(Diet.MEAT);
        cookbook(vegan, vegetarian, meat);

        var pool = cut.of(OWNER, List.of(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(), null,
                Diet.VEGETARIAN, null, null, null, 10, 1L));

        assertEquals(List.of("Ofengemüse", "Käsespätzle"), titlesOf(pool));
    }

    @Test
    void aRecipeWithNoTimeRecordedSurvivesATimeBudget() {
        var timed = recipe(1, "Schnell");
        timed.setTotalTime(20L);
        var slow = recipe(2, "Schmorbraten");
        slow.setTotalTime(180L);
        var unknown = recipe(3, "Importiert");
        cookbook(timed, slow, unknown);

        var pool = cut.of(OWNER, List.of(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(), 30L,
                null, null, null, null, 10, 1L));

        assertEquals(List.of("Schnell", "Importiert"), titlesOf(pool));
    }

    @Test
    void preparationTimeStandsInWhereThereIsNoTotalTime() {
        var recipe = recipe(1, "Salat");
        recipe.setPreparationTime(15L);
        cookbook(recipe);

        var pool = cut.of(OWNER, List.of(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(), 30L,
                null, null, null, null, 10, 1L));

        assertEquals(List.of("Salat"), titlesOf(pool));
    }

    @Test
    void aRecipeWithoutMealTypesStillCountsAsDinner() {
        cookbook(recipe(1, "Irgendwas"));

        var pool = cut.of(OWNER, List.of(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(), null,
                null, Set.of(MealType.DINNER), null, null, 10, 1L));

        assertEquals(List.of("Irgendwas"), titlesOf(pool));
    }

    @Test
    void aRecipeWithoutMealTypesIsNotOfferedForBreakfast() {
        var unknown = recipe(1, "Irgendwas");
        var stated = recipe(2, "Porridge");
        stated.setMealTypes(Set.of(MealType.BREAKFAST));
        cookbook(unknown, stated);

        var pool = cut.of(OWNER, List.of(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(), null,
                null, Set.of(MealType.BREAKFAST), null, null, 10, 1L));

        assertEquals(List.of("Porridge"), titlesOf(pool));
    }

    @Test
    void anyRankedKeepsEveryRecipeUsingAtLeastOneWantedIngredient() {
        cookbook(recipe(1, "Tomatensuppe", linked(10, "Tomate", TOMATO)),
                recipe(2, "Tomate-Feta", linked(11, "Tomate", TOMATO), linked(12, "Feta", FETA)),
                recipe(3, "Pfannkuchen"));

        var pool = cut.of(OWNER, targets(), criteria(MatchMode.ANY_RANKED, List.of(1L, 2L)));

        assertEquals(List.of("Tomatensuppe", "Tomate-Feta"), titlesOf(pool));
        assertTrue(pool.nearMisses().isEmpty());
    }

    @Test
    void mustContainKeepsOnlyRecipesWithAllOfThemAndReportsTheOnesOneShort() {
        cookbook(recipe(1, "Tomatensuppe", linked(10, "Tomate", TOMATO)),
                recipe(2, "Tomate-Feta", linked(11, "Tomate", TOMATO), linked(12, "Feta", FETA)),
                recipe(3, "Pfannkuchen"));

        var pool = cut.of(OWNER, targets(), criteria(MatchMode.MUST_CONTAIN, List.of(1L, 2L)));

        assertEquals(List.of("Tomate-Feta"), titlesOf(pool));
        assertEquals(List.of("Tomatensuppe"),
                pool.nearMisses().stream().map(candidate -> candidate.recipe().getTitle()).toList());
    }

    /**
     * With one wanted ingredient, "missing exactly one" is every recipe that matches nothing at
     * all - which would put the cook's whole cookbook under a heading promising a near miss.
     */
    @Test
    void oneWantedIngredientHasNoNearMisses() {
        cookbook(recipe(1, "Tomatensuppe", linked(10, "Tomate", TOMATO)), recipe(2, "Pfannkuchen"));

        var pool = cut.of(OWNER, List.of(new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId()))),
                criteria(MatchMode.MUST_CONTAIN, List.of(1L)));

        assertEquals(List.of("Tomatensuppe"), titlesOf(pool));
        assertTrue(pool.nearMisses().isEmpty());
    }

    @Test
    void poolStatsSeparateWhatTheFiltersRemovedFromWhatTheIngredientsDid() {
        var slow = recipe(1, "Schmorbraten");
        slow.setTotalTime(180L);
        cookbook(slow, recipe(2, "Tomatensuppe", linked(10, "Tomate", TOMATO)), recipe(3, "Pfannkuchen"));

        var pool = cut.of(OWNER, targets(), new SuggestionCriteria(MatchMode.ANY_RANKED, List.of(1L, 2L), 30L,
                null, null, null, null, 10, 1L));

        assertEquals(3, pool.owned());
        assertEquals(2, pool.afterFilters());
        assertEquals(1, pool.hits().size());
    }

    private static List<MatchTarget> targets() {
        return List.of(new MatchTarget(1L, "Tomate", Set.of(TOMATO.getId())),
                new MatchTarget(2L, "Feta", Set.of(FETA.getId())));
    }
}
