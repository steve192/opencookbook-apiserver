package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.requests.IngredientRequest;
import com.sterul.opencookbookapiserver.controllers.responses.IngredientResponse;
import com.sterul.opencookbookapiserver.repositories.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/ingredients")
@Tag(name = "Ingredients", description = "Ingredients used in recipes")
public class IngredientsController extends BaseController {

    @Autowired
    private IngredientService ingredientService;

    @Operation(summary = "Get all ingredients accessible by logged in user")
    @GetMapping("")
    public List<IngredientResponse> all() {
        return ingredientService.getUserPermittedIngredients(getLoggedInUser()).stream().map(this::entityToResponse).toList();
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
