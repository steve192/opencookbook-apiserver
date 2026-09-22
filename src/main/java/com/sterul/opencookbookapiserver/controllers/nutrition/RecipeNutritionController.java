package com.sterul.opencookbookapiserver.controllers.nutrition;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeNutritionResponse;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/recipes")
@ConditionalOnNutritionEnabled
@Tag(name = "Nutrition", description = "Estimated nutrients of recipes, and the links behind them")
public class RecipeNutritionController extends BaseController {

    private final RecipeService recipeService;
    private final NutritionCalculator calculator;
    private final NutritionDatasetReader datasetReader;
    private final MailLanguages languages;

    public RecipeNutritionController(RecipeService recipeService, NutritionCalculator calculator,
            NutritionDatasetReader datasetReader, MailLanguages languages) {
        this.recipeService = recipeService;
        this.calculator = calculator;
        this.datasetReader = datasetReader;
        this.languages = languages;
    }

    @Operation(summary = "The estimated nutrients of one of your recipes, line by line")
    @GetMapping("/{id}/nutrition")
    public RecipeNutritionResponse nutrition(@PathVariable Long id) throws ElementNotFound {
        var user = getLoggedInUser();
        return RecipeNutritionResponse.forOwner(calculator.calculate(recipeService.getRecipeFor(id, user)),
                languages.forUser(user).getLanguage(), datasetReader.manifest());
    }
}
