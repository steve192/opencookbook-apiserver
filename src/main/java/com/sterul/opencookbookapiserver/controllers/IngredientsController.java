package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

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
    public List<Ingredient> all() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public List<Ingredient> all(@PathVariable Long id) {
        return repository.findAll();
    }

    @PostMapping("")
    public Ingredient create(@RequestBody Ingredient newIngredient) {
        return repository.save(newIngredient);
    }

    public IngredientsController(IngredientRepository repository) {
        this.repository = repository;
    }
}
