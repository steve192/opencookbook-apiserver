package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private  RecipeRepository recipeRepository;

    @Autowired
    private  IngredientRepository ingredientRepository;


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
        for (IngredientNeed ingredientNeed : newRecipe.getNeededIngredients()) {
            var ingredient = ingredientNeed.getIngredient();
            if (ingredient.getId() == null){
                // Convenience api which creates ingredients
                ingredientNeed.setIngredient(ingredientRepository.save(ingredient));
            }
        }
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
