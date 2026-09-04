package com.sterul.opencookbookapiserver.services.ml.recipeocr;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.ml.MlSubsystemException;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.IngredientExtractor;
import com.sterul.opencookbookapiserver.util.IngredientUnitHelper;

import lombok.extern.slf4j.Slf4j;

/** Turns the subsystem's reading of a photograph into a recipe the wizard can be opened with. */
@Service
@ConditionalOnMlConfigured
@Slf4j
public class RecipeOcrImportService {

    private static final int DEFAULT_SERVINGS = 1;

    private final Gson gson = new Gson();

    /**
     * Reads the subsystem's answer without interpreting it.
     *
     * @param resultJson what the subsystem returned
     * @return the parsed answer
     * @throws MlSubsystemException when it cannot be read
     */
    public RecipeOcrResult read(String resultJson) throws MlSubsystemException {
        try {
            var result = gson.fromJson(resultJson, RecipeOcrResult.class);
            if (result == null) {
                throw new MlSubsystemException("ML_EMPTY_RESULT",
                        "The subsystem returned no recipe", false);
            }
            return result;
        } catch (JsonSyntaxException e) {
            throw new MlSubsystemException("ML_MALFORMED_RESULT",
                    "The subsystem's recipe could not be read", false, e);
        }
    }

    public Recipe toRecipe(RecipeOcrResult result, CookpalUser owner) {
        var recipe = Recipe.builder()
                .owner(owner)
                .title(textOf(result.getTitle()))
                .servings(servingsOf(result))
                .preparationTime(minutesOf(result.getPreparationTime()))
                .totalTime(minutesOf(result.getTotalTime()))
                .preparationSteps(stepsOf(result))
                .build();

        recipe.setNeededIngredients(ingredientsOf(result));
        return recipe;
    }

    private List<IngredientNeed> ingredientsOf(RecipeOcrResult result) {
        var needs = new ArrayList<IngredientNeed>();
        for (var parsed : result.getIngredients()) {
            var need = toNeed(parsed);
            if (need != null) {
                needs.add(need);
            }
        }
        return needs;
    }

    /** One ingredient, with the unit checked against what this instance understands. */
    private IngredientNeed toNeed(RecipeOcrResult.Ingredient parsed) {
        var name = trimmed(parsed.getName());
        var unit = trimmed(parsed.getUnit());
        var amount = parsed.getAmount() == null ? 0f : parsed.getAmount().floatValue();
        var additionalInfo = trimmed(parsed.getAdditionalInfo());

        if (!unit.isEmpty() && !IngredientUnitHelper.isKnownUnit(unit)) {
            var raw = trimmed(parsed.getRaw());
            log.debug("Unknown unit '{}' from the subsystem, re-reading '{}'", unit, raw);
            unit = IngredientExtractor.extractUnit(raw);
            amount = IngredientExtractor.extractAmount(raw);
            name = IngredientExtractor.extractName(raw);
            additionalInfo = IngredientExtractor.extractAdditionalInfo(raw);
        }

        if (name.isEmpty()) {
            // Nothing to put in the cookbook; an amount without a name is not an ingredient.
            return null;
        }

        return IngredientNeed.builder()
                .amount(amount)
                .unit(unit)
                .ingredient(Ingredient.builder().name(name).additionalInfo(additionalInfo).build())
                .build();
    }

    private List<String> stepsOf(RecipeOcrResult result) {
        return result.getPreparationSteps().stream()
                .map(RecipeOcrResult.Field::asText)
                .filter(step -> step != null && !step.isBlank())
                .toList();
    }

    private int servingsOf(RecipeOcrResult result) {
        var servings = result.getServings() == null ? null : result.getServings().asInteger();
        // A recipe for nobody cannot be scaled, and the wizard would show an empty field.
        return servings == null || servings < 1 ? DEFAULT_SERVINGS : servings;
    }

    private Long minutesOf(RecipeOcrResult.Field field) {
        var minutes = field == null ? null : field.asInteger();
        return minutes == null || minutes < 0 ? 0L : minutes.longValue();
    }

    private String textOf(RecipeOcrResult.Field field) {
        return field == null ? "" : trimmed(field.asText());
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
