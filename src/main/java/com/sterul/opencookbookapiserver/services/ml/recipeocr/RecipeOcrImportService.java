package com.sterul.opencookbookapiserver.services.ml.recipeocr;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.services.ml.MlSubsystemException;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader.IngredientLine;

import lombok.extern.slf4j.Slf4j;

/** Turns the subsystem's reading of a photograph into a recipe the wizard can be opened with. */
@Service
@ConditionalOnMlConfigured
@Slf4j
public class RecipeOcrImportService {

    private static final int DEFAULT_SERVINGS = 1;

    private final Gson gson = new Gson();
    private final IngredientLineReader lineReader;
    private final UnitLexicon unitLexicon;

    public RecipeOcrImportService(IngredientLineReader lineReader, UnitLexicon unitLexicon) {
        this.lineReader = lineReader;
        this.unitLexicon = unitLexicon;
    }

    /**
     * Reads the subsystem's answer without interpreting it.
     *
     * @param resultJson what the subsystem returned
     * @return the parsed answer
     * @throws MlSubsystemException when it cannot be read
     */
    public RecipeOcrResult read(String resultJson) {
        try {
            var result = gson.fromJson(resultJson, RecipeOcrResult.class);
            if (result == null) {
                throw new MlSubsystemException(ApiErrorCode.SCAN_NO_TEXT_FOUND,
                        "The subsystem returned no recipe");
            }
            return result;
        } catch (JsonSyntaxException e) {
            throw new MlSubsystemException(ApiErrorCode.SCAN_FAILED,
                    "The subsystem's recipe could not be read", e);
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

        recipe.setNeededIngredients(ingredientsOf(result, owner.getLanguage()));
        return recipe;
    }

    private List<IngredientNeed> ingredientsOf(RecipeOcrResult result, String language) {
        return result.getIngredients().stream()
                .flatMap(parsed -> lineOf(parsed, language).map(IngredientLine::toNeed).stream())
                .toList();
    }

    /** One ingredient, with the unit checked against what this instance understands; empty without a name. */
    private Optional<IngredientLine> lineOf(RecipeOcrResult.Ingredient parsed, String language) {
        var unit = trimmed(parsed.getUnit());
        if (!unit.isEmpty() && !unitLexicon.isKnownUnit(unit)) {
            var raw = trimmed(parsed.getRaw());
            log.debug("Unknown unit '{}' from the subsystem, re-reading '{}'", unit, raw);
            return lineReader.read(raw, language);
        }
        var amount = parsed.getAmount() == null ? null : parsed.getAmount().floatValue();
        return lineReader.read(amount, unit, parsed.getName(), parsed.getAdditionalInfo(), language);
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
