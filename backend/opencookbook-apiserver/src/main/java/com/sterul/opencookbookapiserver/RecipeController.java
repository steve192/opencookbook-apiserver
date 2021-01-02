package com.sterul.opencookbookapiserver;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.Recipe;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class RecipeController {
    private final RecipeRepository recipeRepository;
    private final IngredientNeedRepository ingredientNeedRepository;

    RecipeController(RecipeRepository repository, IngredientNeedRepository ingredientNeedRepository) {
        this.recipeRepository = repository;
        this.ingredientNeedRepository = ingredientNeedRepository;
    }

    @GetMapping("/recipes")
    List<Recipe> all() {
        return recipeRepository.findAll();
    }

    @PostMapping("/recipes")
    Recipe newRecipe(@RequestBody Recipe newRecipe) {
        for (IngredientNeed ingredientNeed : newRecipe.getNeededIngredients()) {
            ingredientNeedRepository.save(ingredientNeed);
        }
        return recipeRepository.save(newRecipe);
    }

    @GetMapping("/recipes/{id}")
    Recipe single(@PathVariable Long id) {
        return recipeRepository.findById(id).get();
    }

    @PutMapping("/recipes/{id}")
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
