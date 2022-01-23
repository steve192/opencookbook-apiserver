package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import com.sterul.opencookbookapiserver.controllers.requests.IngredientRequest;
import com.sterul.opencookbookapiserver.controllers.responses.IngredientResponse;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientsController {

    @Autowired
    private IngredientService ingredientService;

    @GetMapping("")
    public List<IngredientResponse> all() {
        return ingredientService.getAllIngredients().stream().map(this::entityToResponse).toList();
    }

    @GetMapping("/{id}")
    public IngredientResponse single(@PathVariable Long id) throws ElementNotFound {
        return entityToResponse(ingredientService.getIngredient(id));
    }

    @PostMapping("")
    public IngredientResponse create(@RequestBody IngredientRequest newIngredient) {
        return entityToResponse(
                ingredientService.createIngredient(requestToEntity(newIngredient)));
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
