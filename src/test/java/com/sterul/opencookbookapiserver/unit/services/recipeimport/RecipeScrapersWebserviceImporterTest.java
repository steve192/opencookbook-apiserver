package com.sterul.opencookbookapiserver.unit.services.recipeimport;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScraperServiceProxy;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScrapersWebserviceImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RecipeScrapersWebserviceImporterTest {

    private static final String testRecipeUrl = "https://testing.com/recipe";

    private final String testRecipeJson = """
            {
                "author": "Estrella87",
                "category": "",
                "cook_time": "",
                "cuisine": "",
                "host": "chefkoch.de",
                "ingredients": [
                    "250 g Butter (davon 50 g f\u00fcr Guss)",
                    "300 g Schokolade , bittere (davon 100 g f\u00fcr Guss)",
                    "100 g Schokolade nach Wahl, gehackt",
                    "200 g Zucker", "1 Pck. Vanillezucker",
                    "4 Ei(er)",
                    "1 Prise(n) Salz",
                    "\u00bd Flasche Rumaroma oder 3 EL Rum",
                    "100 g Mehl (oder 60 g Mehl und 40 g gemahlene Haseln\u00fcsse)",
                    "Salz nach Wunsch"
                ],
                "instructions": "Den Ofen auf 160\u00b0C vorheizen.\r
            \r
            200 g Butter und 200 g Bitterschokoladest\u00fcckchen vorsichtig miteinander schmelzen, die Schokolade darf nicht zu hei\u00df werden. Abk\u00fchlen lassen.\r
            \r
            Die Eier mit Zucker, Vanillezucker und Salz sch\u00f6n schaumig r\u00fchren. Die geschmolzene, h\u00f6chstens noch lauwarme  Schokolade und das Rumaroma unterr\u00fchren. Die gehackten Schokost\u00fcckchen und das Mehl unterr\u00fchren.\r
            \r
            Eine Form (rund ca. 26 cm oder eckig ca. 25 x 25 cm) fetten und den Teig einf\u00fcllen. Ca. 35 Minuten backen. Der Kuchen ist genau richtig, wenn er in der Mitte gerade eine nicht mehr ganz fl\u00fcssige Konsistenz angenommen hat. Dann fast ausk\u00fchlen lassen.\r
            \r
            Die verbliebenen 100 g Bitterschokolade und 50 g Butter schmelzen und den Kuchen damit \u00fcberziehen.\r
            \r
            Ruhig einen Tag ziehen lassen! K\u00fchl aufbewahren, aber zimmerwarm servieren!",
                "language": "de",
                "prep_time": "123",
                "nutrients": {
                    "calories": "400 kcal",
                    "servingSize": "1"
                },
                "ratings": 3.82,
                "title": "Der sch\u00f6nste Tod",
                "total_time": 140,
                "yields": "1 serving(s)"
            }
                    """;

    @MockBean
    private RecipeScraperServiceProxy recipeScraperServiceProxy;

    @Autowired
    private RecipeScrapersWebserviceImporter cut;

    @Mock
    private User userMock;

    @BeforeEach
    public void setup() throws IOException, ImportNotSupportedException {
        when(recipeScraperServiceProxy.scrapeRecipe(testRecipeUrl)).thenReturn(testRecipeJson);
    }

    @Test
    void testPreparationStepsImported() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, null);

        assertEquals(Arrays.asList("Den Ofen auf 160°C vorheizen.",
                        "200 g Butter und 200 g Bitterschokoladestückchen vorsichtig miteinander schmelzen, die Schokolade darf nicht zu heiß werden. Abkühlen lassen.",
                        "Die Eier mit Zucker, Vanillezucker und Salz schön schaumig rühren. Die geschmolzene, höchstens noch lauwarme  Schokolade und das Rumaroma unterrühren. Die gehackten Schokostückchen und das Mehl unterrühren.",
                        "Eine Form (rund ca. 26 cm oder eckig ca. 25 x 25 cm) fetten und den Teig einfüllen. Ca. 35 Minuten backen. Der Kuchen ist genau richtig, wenn er in der Mitte gerade eine nicht mehr ganz flüssige Konsistenz angenommen hat. Dann fast auskühlen lassen.",
                        "Die verbliebenen 100 g Bitterschokolade und 50 g Butter schmelzen und den Kuchen damit überziehen.",
                        "Ruhig einen Tag ziehen lassen! Kühl aufbewahren, aber zimmerwarm servieren!"),
                recipe.getPreparationSteps());
    }

    @Test
    void testServingsImported() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, userMock);
        assertEquals(1, recipe.getServings());
    }

    @Test
    void testIngredientsImported() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, userMock);

        assertIngredientPresent(recipe, 250, "g", "Butter (davon 50 g für Guss)", 0);
        assertIngredientPresent(recipe, 300, "g", "Schokolade , bittere (davon 100 g für Guss)", 1);
        assertIngredientPresent(recipe, 100, "g", "Schokolade nach Wahl, gehackt", 2);
        assertIngredientPresent(recipe, 200, "g", "Zucker", 3);
        assertIngredientPresent(recipe, 1, "Pck.", "Vanillezucker", 4);
        assertIngredientPresent(recipe, 4, "", "Ei(er)", 5);
        assertIngredientPresent(recipe, 1, "Prise(n)", "Salz", 6);
        assertIngredientPresent(recipe, 0.5F, "Flasche", "Rumaroma oder 3 EL Rum", 7);
        assertIngredientPresent(recipe, 0, "", "Salz nach Wunsch", 9);
    }

    private void assertIngredientPresent(Recipe recipe, float amount, String unit, String ingredientName, int index) {
        assertEquals(amount, recipe.getNeededIngredients().get(index).getAmount());
        assertEquals(unit, recipe.getNeededIngredients().get(index).getUnit());
        assertEquals(ingredientName, recipe.getNeededIngredients().get(index).getIngredient().getName());
    }

    @Test
    void testTitleImported() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, userMock);
        assertEquals("Der schönste Tod", recipe.getTitle());
    }

    @Test
    void testTimesImported() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, userMock);
        assertEquals(140, recipe.getTotalTime());
        assertEquals(123, recipe.getPreparationTime());
    }

    @Test
    void testRecipeGroupNotSet() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, userMock);
        assertEquals(0, recipe.getRecipeGroups().size());
    }

    @Test
    void testOwnerSet() throws RecipeImportFailedException {
        var recipe = cut.importRecipe(testRecipeUrl, userMock);
        assertEquals(userMock, recipe.getOwner());
    }

}
