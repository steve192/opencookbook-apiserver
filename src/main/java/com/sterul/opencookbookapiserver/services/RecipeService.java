package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeService {

    @Autowired
    IngredientRepository ingredientRepository;

    @Autowired
    RecipeRepository recipeRepository;

    public Recipe createNewRecipe(Recipe newRecipe) {
        for (var ingredientNeed : newRecipe.getNeededIngredients()) {
            var ingredient = ingredientNeed.getIngredient();
            if (ingredient.getId() == null) {
                // Convenience api which creates ingredients
                ingredientNeed.setIngredient(ingredientRepository.save(ingredient));
            }
        }
        return recipeRepository.save(newRecipe);
    }
    
}
