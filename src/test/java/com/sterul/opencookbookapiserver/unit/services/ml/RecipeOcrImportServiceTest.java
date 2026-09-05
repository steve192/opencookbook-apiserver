package com.sterul.opencookbookapiserver.unit.services.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.ml.MlSubsystemException;
import com.sterul.opencookbookapiserver.services.ml.recipeocr.RecipeOcrImportService;

/**
 * Turning the subsystem's reading of a photograph into a recipe.
 *
 * The interesting part is what happens to a unit this instance does not know: the line is read
 * again here, so that a scan and a url import end up speaking the same vocabulary.
 */
class RecipeOcrImportServiceTest {

    private final RecipeOcrImportService cut = new RecipeOcrImportService();
    private final CookpalUser owner = new CookpalUser();

    private static final String FULL_RESULT = """
            {
              "language": "de",
              "modelVersion": "rapidocr-ppocrv6-medium",
              "pageCount": 1,
              "title": {"value": "Apfelkuchen", "confidence": 0.91},
              "servings": {"value": 12, "confidence": 0.88},
              "preparationTime": {"value": 30, "confidence": 0.85},
              "totalTime": {"value": 90, "confidence": 0.6},
              "ingredients": [
                {"raw": "200 g Mehl (Type 405)", "amount": 200.0, "unit": "g",
                 "name": "Mehl", "additionalInfo": "Type 405", "confidence": 0.94},
                {"raw": "2 Eier", "amount": 2.0, "unit": "",
                 "name": "Eier", "additionalInfo": "", "confidence": 0.85}
              ],
              "preparationSteps": [
                {"value": "Den Ofen vorheizen.", "confidence": 0.95},
                {"value": "Alles verruehren.", "confidence": 0.93}
              ],
              "rawText": "Apfelkuchen"
            }
            """;

    @Test
    void everyFieldReachesTheRecipe() throws MlSubsystemException {
        var recipe = toRecipe(FULL_RESULT);

        assertEquals("Apfelkuchen", recipe.getTitle());
        assertEquals(12, recipe.getServings());
        assertEquals(30L, recipe.getPreparationTime());
        assertEquals(90L, recipe.getTotalTime());
        assertEquals(owner, recipe.getOwner());
        assertEquals(List.of("Den Ofen vorheizen.", "Alles verruehren."),
                recipe.getPreparationSteps());
    }

    @Test
    void ingredientsKeepTheirAmountUnitAndDetail() throws MlSubsystemException {
        var needs = toRecipe(FULL_RESULT).getNeededIngredients();

        assertEquals(2, needs.size());
        assertEquals(200f, needs.get(0).getAmount());
        assertEquals("g", needs.get(0).getUnit());
        assertEquals("Mehl", needs.get(0).getIngredient().getName());
        assertEquals("Type 405", needs.get(0).getIngredient().getAdditionalInfo());
        assertEquals("", needs.get(1).getUnit());
    }

    @Test
    void aUnitThisInstanceDoesNotKnowMakesTheLineBeReadAgainHere() throws MlSubsystemException {
        // "oz" has no entry in cookpal's vocabulary, so the proposal cannot be used as it is.
        var result = ingredientResult(
                "{\"raw\": \"8 oz Frischkaese\", \"amount\": 8.0, \"unit\": \"oz\","
                        + " \"name\": \"Frischkaese\", \"additionalInfo\": \"\","
                        + " \"confidence\": 0.9}");

        var need = toRecipe(result).getNeededIngredients().get(0);

        assertEquals("", need.getUnit());
        assertTrue(need.getIngredient().getName().contains("Frischkaese"));
    }

    @Test
    void aKnownUnitIsTakenAsProposed() throws MlSubsystemException {
        var result = ingredientResult(
                "{\"raw\": \"1 Prise Salz\", \"amount\": 1.0, \"unit\": \"Prise(n)\","
                        + " \"name\": \"Salz\", \"additionalInfo\": \"\", \"confidence\": 0.9}");

        var need = toRecipe(result).getNeededIngredients().get(0);

        assertEquals("Prise(n)", need.getUnit());
        assertEquals("Salz", need.getIngredient().getName());
        assertEquals(1f, need.getAmount());
    }

    @Test
    void anIngredientWithoutANameIsLeftOut() throws MlSubsystemException {
        var result = ingredientResult(
                "{\"raw\": \"200\", \"amount\": 200.0, \"unit\": \"\", \"name\": \"\","
                        + " \"additionalInfo\": \"\", \"confidence\": 0.2}");

        assertTrue(toRecipe(result).getNeededIngredients().isEmpty());
    }

    @Test
    void aFieldTheSubsystemWasNotSureOfArrivesEmpty() throws MlSubsystemException {
        // Below its own threshold the subsystem sends null, and this side must not invent one.
        var result = """
                {"title": {"value": null, "confidence": 0.3},
                 "servings": {"value": null, "confidence": 0.0},
                 "preparationTime": {"value": null, "confidence": 0.0},
                 "totalTime": {"value": null, "confidence": 0.0},
                 "ingredients": [], "preparationSteps": []}
                """;

        var recipe = toRecipe(result);

        assertEquals("", recipe.getTitle());
        assertEquals(0L, recipe.getPreparationTime());
    }

    @Test
    void aRecipeIsAlwaysForAtLeastOnePerson() throws MlSubsystemException {
        var result = """
                {"servings": {"value": 0, "confidence": 0.9},
                 "ingredients": [], "preparationSteps": []}
                """;

        assertEquals(1, toRecipe(result).getServings());
    }

    @Test
    void theScannedRecipeIsNotGivenAnIdBecauseItIsNotSavedYet() throws MlSubsystemException {
        assertNull(toRecipe(FULL_RESULT).getId());
    }

    @Test
    void theBlocksTheAppAsksAboutAreReadBack() throws MlSubsystemException {
        var withBlocks = """
                {"ingredients": [], "preparationSteps": [],
                 "blocks": {
                   "ingredients": [
                     {"pageIndex": 0, "lineCount": 12,
                      "box": {"left": 0.05, "top": 0.2, "right": 0.45, "bottom": 0.9}},
                     {"pageIndex": 0, "lineCount": 7,
                      "box": {"left": 0.55, "top": 0.1, "right": 0.9, "bottom": 0.4}}
                   ],
                   "steps": null}}
                """;

        var blocks = cut.read(withBlocks).getBlocks();

        // Two areas: a list printed across two columns is not one rectangle.
        assertEquals(2, blocks.getIngredients().size());
        assertEquals(12, blocks.getIngredients().get(0).getLineCount());
        assertEquals(0.05, blocks.getIngredients().get(0).getBox().getLeft());
        assertEquals(0.55, blocks.getIngredients().get(1).getBox().getLeft());
        // Null is an answer: nothing that looked like a method was found.
        assertNull(blocks.getSteps());
    }

    @Test
    void anUnreadableResultIsReportedRatherThanGuessedAt() {
        assertThrows(MlSubsystemException.class, () -> toRecipe("{not json"));
    }

    @Test
    void aPhotographWithNoIngredientsAndNoStepsInItStillMakesARecipe() throws MlSubsystemException {
        // Null, not an empty list: that is how the subsystem says it found none of that kind,
        // and gson takes a written null over the field's initialiser.
        var recipe = toRecipe("""
                {"title": {"value": "Nur ein Titel"},
                 "ingredients": null, "preparationSteps": null}
                """);

        assertEquals("Nur ein Titel", recipe.getTitle());
        assertEquals(List.of(), recipe.getNeededIngredients());
        assertEquals(List.of(), recipe.getPreparationSteps());
    }

    @Test
    void aResultThatMentionsNeitherListAtAllIsTheSame() throws MlSubsystemException {
        var recipe = toRecipe("{\"title\": {\"value\": \"Nur ein Titel\"}}");

        assertEquals(List.of(), recipe.getNeededIngredients());
        assertEquals(List.of(), recipe.getPreparationSteps());
    }

    /** Read the subsystem's answer, then turn it into a recipe - what a scan does in one go. */
    private Recipe toRecipe(String resultJson) throws MlSubsystemException {
        return cut.toRecipe(cut.read(resultJson), owner);
    }

    private String ingredientResult(String ingredientJson) {
        return "{\"ingredients\": [" + ingredientJson + "], \"preparationSteps\": []}";
    }
}
