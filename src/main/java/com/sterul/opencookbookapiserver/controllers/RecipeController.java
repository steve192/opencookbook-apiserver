package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {
    private final RecipeRepository recipeRepository;

    RecipeController(RecipeRepository repository) {
        this.recipeRepository = repository;
    }

    @GetMapping("")
    List<Recipe> searchRecipe(@RequestParam(required = false) String searchString) {
        if (searchString != null) {
            return recipeRepository.findByTitleIgnoreCaseContaining(searchString);
        } else {
            return recipeRepository.findAll();
        }
    }

    @PostMapping("") 
    Recipe newRecipe(@RequestBody Recipe newRecipe) {
        return recipeRepository.save(newRecipe);
    }

    @GetMapping("/{id}")
    Recipe single(@PathVariable Long id) {
        return recipeRepository.findById(id).get();
    }

    @PutMapping("/{id}")
    Recipe updateRecipe(@PathVariable Long id, @RequestBody Recipe recipeUpdate) {
        return recipeRepository.findById(id).map(recipe -> {
            recipe.setTitle(recipeUpdate.getTitle());
            return recipeRepository.save(recipe);
        }).orElseGet(() -> {
            recipeUpdate.setId(id);
            return recipeRepository.save(recipeUpdate);
        });
        
    }

    
}
