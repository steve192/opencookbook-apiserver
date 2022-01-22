package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import com.sterul.opencookbookapiserver.controllers.requests.IngredientRequest;
import com.sterul.opencookbookapiserver.controllers.responses.IngredientResponse;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientsController {

    private IngredientRepository repository;

    @GetMapping("")
    public List<IngredientResponse> all() {
        return repository.findAll().stream().map(this::entityToResponse).toList();
    }

    @GetMapping("/{id}")
    public List<IngredientResponse> all(@PathVariable Long id) {
        return repository.findAll().stream().map(this::entityToResponse).toList();
    }

    @PostMapping("")
    public IngredientResponse create(@RequestBody IngredientRequest newIngredient) {
        return entityToResponse(
                repository.save(requestToEntity(newIngredient)));
    }

    public IngredientsController(IngredientRepository repository) {
        this.repository = repository;
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
