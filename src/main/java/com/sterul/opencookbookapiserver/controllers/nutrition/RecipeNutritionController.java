package com.sterul.opencookbookapiserver.controllers.nutrition;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.responses.NutritionOfRecipeResponse;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeNutritionResponse;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/recipes")
@Tag(name = "Nutrition", description = "Estimated nutrients of recipes, and the links behind them")
public class RecipeNutritionController extends BaseController {

    private final RecipeService recipeService;
    private final NutritionCalculator calculator;
    private final CatalogueDatasetReader datasetReader;
    private final MailLanguages languages;

    public RecipeNutritionController(RecipeService recipeService, NutritionCalculator calculator,
            CatalogueDatasetReader datasetReader, MailLanguages languages,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.recipeService = recipeService;
        this.calculator = calculator;
        this.datasetReader = datasetReader;
        this.languages = languages;
    }

    @Operation(summary = "The estimated nutrients of every recipe you can read, line by line")
    @GetMapping("/nutrition")
    public List<NutritionOfRecipeResponse> nutrition() {
        var user = getLoggedInUser();
        var language = languages.forUser(user).getLanguage();
        var manifest = datasetReader.manifest();
        return recipeService.readableBy(user).stream()
                .map(readable -> new NutritionOfRecipeResponse(readable.recipe().getId(),
                        RecipeNutritionResponse.forReader(calculator.calculate(readable.recipe()), language, manifest)))
                .toList();
    }
}
