package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.services.nutrition.classification.DietPreview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/recipes/diet-preview")
@Tag(name = "Recipes", description = "Recipe api")
@ConditionalOnNutritionEnabled
public class RecipeDietPreviewController extends BaseController {

    private static final int MAX_INGREDIENTS = 200;

    private final DietPreview dietPreview;

    public RecipeDietPreviewController(DietPreview dietPreview) {
        this.dietPreview = dietPreview;
    }

    public record DietPreviewRequest(@NotNull @Size(max = MAX_INGREDIENTS) List<@NotBlank String> ingredientNames) {
    }

    /** @param diet null where the ingredients do not tell: one is not linked to the catalogue, or there are none */
    public record DietPreviewResponse(Diet diet) {
    }

    @Operation(summary = "The diet a recipe with these ingredients would have",
            description = "Read from the catalogue as saving would link the ingredients; nothing is stored. "
                    + "Only where nutrition estimation is switched on.")
    @PostMapping
    public DietPreviewResponse preview(@Valid @RequestBody DietPreviewRequest request) {
        return new DietPreviewResponse(dietPreview.of(request.ingredientNames(), getLoggedInUser()).orElse(null));
    }
}
