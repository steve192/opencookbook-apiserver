package com.sterul.opencookbookapiserver.controllers.sharing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeNutritionResponse;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Read-only and without ids; does not count as opening the share. */
@RestController
@ConditionalOnProperty(prefix = "opencookbook.sharing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnNutritionEnabled
@RequestMapping(SharePaths.PUBLIC_BASE)
@Tag(name = "Shared recipes", description = "Reading a recipe somebody shared with you")
public class SharedRecipeNutritionController {

    private final ShareService shareService;
    private final NutritionCalculator calculator;
    private final NutritionDatasetReader datasetReader;
    private final MailLanguages languages;

    public SharedRecipeNutritionController(ShareService shareService, NutritionCalculator calculator,
            NutritionDatasetReader datasetReader, MailLanguages languages) {
        this.shareService = shareService;
        this.calculator = calculator;
        this.datasetReader = datasetReader;
        this.languages = languages;
    }

    @Operation(summary = "The estimated nutrients of a shared recipe", description = "No authentication required.")
    @GetMapping("/{" + SharePaths.SHARE_ID_VARIABLE + "}/nutrition")
    public RecipeNutritionResponse nutrition(@Valid @NotBlank @PathVariable String shareId) {
        return RecipeNutritionResponse.forShared(calculator.calculate(shareService.resolveSharedRecipe(shareId)),
                languages.forCurrentRequest().getLanguage(), datasetReader.manifest());
    }
}
