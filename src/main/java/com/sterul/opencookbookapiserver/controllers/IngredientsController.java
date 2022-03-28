package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.requests.IngredientRequest;
import com.sterul.opencookbookapiserver.controllers.responses.IngredientResponse;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ingredients")
@Tag(name = "Ingredients", description = "Ingredients used in recipes")
public class IngredientsController extends BaseController {

    @Autowired
    private IngredientService ingredientService;

    @Operation(summary = "Get all ingredients")
    @GetMapping("")
    public List<IngredientResponse> all() {
        return ingredientService.getAllIngredients().stream().map(this::entityToResponse).toList();
    }

    @Operation(summary = "Get a single ingredient")
    @GetMapping("/{id}")
    public IngredientResponse single(@PathVariable Long id) throws ElementNotFound {
        return entityToResponse(ingredientService.getIngredient(id));
    }

    @Operation(summary = "Create an ingredient", description = "If an ingredient with the same name exists, the existing ingredient will be returned")
    @PostMapping("")
    public IngredientResponse create(@RequestBody IngredientRequest newIngredient) {
        return entityToResponse(
                ingredientService.createOrGetIngredient(requestToEntity(newIngredient), getLoggedInUser()));
    }

    private Ingredient requestToEntity(IngredientRequest ingredientRequest) {
        return Ingredient.builder()
                .name(ingredientRequest.getName())
                .build();
    }

    private IngredientResponse entityToResponse(Ingredient ingredient) {
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .build();
    }

}
