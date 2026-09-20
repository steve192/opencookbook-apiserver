package com.sterul.opencookbookapiserver.unit.services.nutrition.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.nutrition.classification.RecipeDietDeriver;

/**
 * Reading a recipe's diet off the catalogue.
 *
 * The rule worth pinning is the one that costs coverage: a recipe is classified only when every
 * counting ingredient is linked. Anything looser would let one unlinked "Speck" pass a pork dish
 * off as vegan, and a diet filter that is confidently wrong is worse than one that says nothing.
 */
class RecipeDietDeriverTest {

    private final RecipeDietDeriver cut = new RecipeDietDeriver();

    private static Ingredient linked(String name, Diet dietClass) {
        return Ingredient.builder().name(name)
                .catalogueFood(CatalogueFood.builder().catalogueKey("bls-" + name).dietClass(dietClass).build())
                .build();
    }

    private static Ingredient unlinked(String name) {
        return Ingredient.builder().name(name).build();
    }

    private static Ingredient excluded(String name) {
        return Ingredient.builder().name(name).excludedFromNutrition(true).build();
    }

    private static Recipe recipe(Ingredient... ingredients) {
        return Recipe.builder().title("test").neededIngredients(Arrays.stream(ingredients)
                .map(ingredient -> IngredientNeed.builder().ingredient(ingredient).build())
                .collect(Collectors.toCollection(ArrayList::new))).build();
    }

    @Test
    void theStrictestIngredientDecides() {
        assertEquals(Optional.of(Diet.MEAT),
                cut.derive(recipe(linked("Tomate", Diet.VEGAN), linked("Sahne", Diet.VEGETARIAN),
                        linked("Hackfleisch", Diet.MEAT))));
        assertEquals(Optional.of(Diet.VEGETARIAN),
                cut.derive(recipe(linked("Tomate", Diet.VEGAN), linked("Sahne", Diet.VEGETARIAN))));
        assertEquals(Optional.of(Diet.VEGAN),
                cut.derive(recipe(linked("Tomate", Diet.VEGAN), linked("Basilikum", Diet.VEGAN))));
    }

    @Test
    void theOrderOfTheIngredientsDoesNotMatter() {
        assertEquals(cut.derive(recipe(linked("Hackfleisch", Diet.MEAT), linked("Tomate", Diet.VEGAN))),
                cut.derive(recipe(linked("Tomate", Diet.VEGAN), linked("Hackfleisch", Diet.MEAT))));
    }

    @Test
    void oneUnlinkedIngredientLeavesTheRecipeUnclassified() {
        assertTrue(cut.derive(recipe(linked("Tomate", Diet.VEGAN), unlinked("Omas Gewürz"))).isEmpty());
    }

    /**
     * Even where the known ingredients already reach MEAT and nothing could raise it further. The
     * stricter rule is the one a reviewer can state in a sentence, and coverage is recovered by
     * linking the ingredient rather than by reasoning about what an unknown one cannot be.
     */
    @Test
    void anUnlinkedIngredientStopsTheReadingEvenWhenTheRestIsAlreadyMeat() {
        assertTrue(cut.derive(recipe(linked("Hackfleisch", Diet.MEAT), unlinked("Omas Gewürz"))).isEmpty());
    }

    @Test
    void aLinkedFoodWithoutADietClassCountsAsUnreadable() {
        var custom = Ingredient.builder().name("Eigenes")
                .catalogueFood(CatalogueFood.builder().catalogueKey("custom-1").build()).build();

        assertTrue(cut.derive(recipe(linked("Tomate", Diet.VEGAN), custom)).isEmpty());
    }

    @Test
    void anIngredientItsOwnerExcludedFromNutritionSaysNothingEitherWay() {
        assertEquals(Optional.of(Diet.VEGAN),
                cut.derive(recipe(linked("Tomate", Diet.VEGAN), excluded("Zahnstocher"))));
    }

    /** "Vegan" would be a claim about no evidence at all. */
    @Test
    void aRecipeWithNothingToReadIsNotVegan() {
        assertTrue(cut.derive(recipe()).isEmpty());
        assertTrue(cut.derive(recipe(excluded("Zahnstocher"))).isEmpty());
    }

    @Test
    void theIngredientThatStoppedTheReadingIsNamedForTheReviewer() {
        var blocked = recipe(linked("Tomate", Diet.VEGAN), unlinked("Suppengrün"), unlinked("Omas Gewürz"));

        assertEquals(Optional.of("Suppengrün"), cut.firstUnreadableIngredient(blocked));
        assertEquals(Optional.empty(), cut.firstUnreadableIngredient(recipe(linked("Tomate", Diet.VEGAN))));
    }
}
