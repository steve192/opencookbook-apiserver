package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse.IngredientNeedResponse;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/recipes/ingredient-lines")
@Tag(name = "Recipes", description = "Recipe api")
public class RecipeIngredientLinesController extends BaseController {

    private static final int MAX_LINES = 200;
    private static final int MAX_LINE_LENGTH = 500;

    private final IngredientLineReader lineReader;

    public RecipeIngredientLinesController(IngredientLineReader lineReader, SignedInUserService signedInUser) {
        super(signedInUser);
        this.lineReader = lineReader;
    }

    public record IngredientLinesRequest(
            @NotNull @Size(max = MAX_LINES) List<@NotBlank @Size(max = MAX_LINE_LENGTH) String> lines) {
    }

    /** @param ingredients in the order of the lines; null where a line names no ingredient */
    public record IngredientLinesResponse(List<IngredientNeedResponse> ingredients) {
    }

    @Operation(summary = "Ingredient lines as a recipe would have them",
            description = "Each line is read into amount, unit, name and note as an import reads it; nothing is stored.")
    @PostMapping
    public IngredientLinesResponse read(@Valid @RequestBody IngredientLinesRequest request) {
        var language = getLoggedInUser().getLanguage();
        return new IngredientLinesResponse(request.lines().stream()
                .map(line -> lineReader.read(line, language)
                        .map(read -> IngredientNeedResponse.fromEntity(read.toNeed()))
                        .orElse(null))
                .toList());
    }
}
