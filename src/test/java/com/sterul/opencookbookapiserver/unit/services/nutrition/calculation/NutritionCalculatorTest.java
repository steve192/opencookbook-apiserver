package com.sterul.opencookbookapiserver.unit.services.nutrition.calculation;

import static com.sterul.opencookbookapiserver.unit.services.nutrition.calculation.Foods.food;
import static com.sterul.opencookbookapiserver.unit.services.nutrition.calculation.Foods.portion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.GramsResolver;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineFlag;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineStatus;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutrition;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;

class NutritionCalculatorTest {

    private final NutritionCalculator calculator = ShippedNutritionDataset.calculator();

    private static final CatalogueFood FLOUR = food("flour", 350);
    private static final CatalogueFood SUGAR = food("sugar", 400);
    private static final CatalogueFood EGG = food("egg", 135, portion("piece", 60));
    private static final CatalogueFood OIL = food("oil", 900);
    private static final CatalogueFood PARSLEY = food("parsley", 33);

    @Test
    void valuesArePerServing() {
        var nutrition = calculator.calculate(recipe(4, line(500f, "g", linked(FLOUR)), line(2f, "", linked(EGG))));

        assertEquals(RecipeNutrition.Basis.SERVING, nutrition.basis());
        assertEquals((5 * 350 + 1.2 * 135) / 4, nutrition.values().energyKcal(), 0.01);
        assertEquals(RecipeNutrition.Status.COMPLETE, nutrition.status());
    }

    @Test
    void aRecipeWithoutServingsHasValuesForTheWholeRecipe() {
        var nutrition = calculator.calculate(recipe(0, line(100f, "g", linked(SUGAR))));

        assertEquals(RecipeNutrition.Basis.RECIPE, nutrition.basis());
        assertEquals(400, nutrition.values().energyKcal(), 0.01);
    }

    @Test
    void anUnlinkedLineWarns() {
        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)), line(100f, "g", linked(SUGAR)),
                line(200f, "g", unlinked("Zauberpulver"))));

        assertEquals(1, nutrition.warningCount());
        assertEquals(RecipeNutrition.Status.INCOMPLETE, nutrition.status());
        assertEquals(LineStatus.UNLINKED, nutrition.lines().get(2).status());
    }

    @Test
    void anUnlinkedLineWarnsOnlyWhereItCouldMatterEvenAsRichAsFat() {
        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)),
                line(1f, "Prise", unlinked("Xanthan")), line(1f, "TL", unlinked("Mirin")),
                line(3f, "EL", unlinked("Worcestersauce")), line(2f, "", unlinked("Sternanis"))));

        assertFalse(nutrition.lines().get(1).warns(), "a pinch");
        assertFalse(nutrition.lines().get(2).warns(), "5 ml at most 45 kcal of 1750");
        assertTrue(nutrition.lines().get(3).warns(), "45 ml up to 405 kcal of 1750");
        assertTrue(nutrition.lines().get(4).warns(), "pieces of unknown weight");
    }

    @Test
    void aMissingAmountWarnsOnlyForARichFood() {
        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)),
                line(null, "etwas", linked(OIL)), line(null, "", linked(PARSLEY))));

        assertTrue(nutrition.lines().get(1).warns(), "etwas Öl");
        assertFalse(nutrition.lines().get(2).warns(), "Petersilie");
    }

    @Test
    void anIngredientUsedOnlyALittleWarnsNeitherForAMissingAmountNorForBeingUnlinked() {
        var flourForTheTin = Ingredient.builder().name("Mehl für die Form").catalogueFood(FLOUR).linkSource(Ingredient.LinkSource.AUTO)
                .linkConfidence(0.95f).build();

        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)), line(null, "etwas", flourForTheTin),
                line(null, "", unlinked("Fett für die Form"))));

        assertEquals(0, nutrition.warningCount());
    }

    @Test
    void aNegligibleFoodNeverWarns() {
        var salt = food("salt", 0);
        salt.setNegligible(true);

        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)), line(1f, "Schnapsglas", linked(salt))));

        assertEquals(0, nutrition.warningCount());
    }

    @Test
    void anExcludedLineCountsForNothingAndDoesNotWarn() {
        var toothpicks = unlinked("Zahnstocher");
        toothpicks.setExcludedFromNutrition(true);

        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)), line(8f, "", toothpicks)));

        assertEquals(RecipeNutrition.Status.COMPLETE, nutrition.status());
        assertEquals(LineStatus.EXCLUDED, nutrition.lines().get(1).status());
    }

    @Test
    void anUncertainLineWarnsOnlyWhenItContributesANoticeableShare() {
        var uncertainSugar = linked(SUGAR);
        uncertainSugar.setLinkConfidence(0.7f);
        var uncertainParsley = linked(PARSLEY);
        uncertainParsley.setLinkConfidence(0.7f);

        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)),
                line(200f, "g", uncertainSugar), line(5f, "g", uncertainParsley)));

        assertEquals(Set.of(LineFlag.LOW_CONFIDENCE), nutrition.lines().get(1).flags());
        assertTrue(nutrition.lines().get(1).warns());
        assertFalse(nutrition.lines().get(2).warns());
    }

    @Test
    void whenHalfTheLinesThatMatterWarnTheValuesAreUnavailable() {
        var nutrition = calculator.calculate(recipe(2, line(500f, "g", linked(FLOUR)), line(1f, "", unlinked("Geheimzutat"))));

        assertEquals(RecipeNutrition.Status.UNAVAILABLE, nutrition.status());
    }

    @Test
    void aRecipeWithoutIngredientsHasNoValues() {
        assertEquals(RecipeNutrition.Status.UNAVAILABLE, calculator.calculate(recipe(2)).status());
    }

    private static Recipe recipe(int servings, IngredientNeed... lines) {
        var recipe = new Recipe();
        recipe.setServings(servings);
        recipe.setNeededIngredients(new ArrayList<>(List.of(lines)));
        return recipe;
    }

    private static IngredientNeed line(Float amount, String unit, Ingredient ingredient) {
        return IngredientNeed.builder().amount(amount).unit(unit).ingredient(ingredient).build();
    }

    private static Ingredient linked(CatalogueFood food) {
        return Ingredient.builder().name(food.getCatalogueKey()).catalogueFood(food).linkSource(Ingredient.LinkSource.AUTO)
                .linkConfidence(0.95f).build();
    }

    private static Ingredient unlinked(String name) {
        return Ingredient.builder().name(name).build();
    }
}
