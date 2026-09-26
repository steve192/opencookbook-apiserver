package com.sterul.opencookbookapiserver.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.requests.RecipeSuggestionRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeSuggestionResponse;
import com.sterul.opencookbookapiserver.controllers.support.RecipeResponses;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.suggestion.RecipeSuggestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/recipes/suggestions")
@Tag(name = "Recipe suggestions", description = "Suggesting what to cook from the caller's own recipes")
public class RecipeSuggestionController extends BaseController {

    private final RecipeSuggestionService suggestionService;
    private final RecipeResponses recipeResponses;

    public RecipeSuggestionController(RecipeSuggestionService suggestionService, RecipeResponses recipeResponses,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.suggestionService = suggestionService;
        this.recipeResponses = recipeResponses;
    }

    @Operation(summary = "Suggest recipes to cook",
            description = "Ranks the caller's own recipes against what they have and want. Wanted ingredients "
                    + "either qualify a recipe on their own (ANY_RANKED) or must all be in it (MUST_CONTAIN); "
                    + "for MUST_CONTAIN, recipes one ingredient short are returned separately as near misses. "
                    + "A POST because the request is structured, not because it changes anything.")
    @PostMapping("")
    public RecipeSuggestionResponse suggest(@Valid @RequestBody RecipeSuggestionRequest request) {
        var result = suggestionService.suggest(request.toCriteria(), getLoggedInUser());
        return RecipeSuggestionResponse.fromResult(result, recipeResponses::of);
    }
}
