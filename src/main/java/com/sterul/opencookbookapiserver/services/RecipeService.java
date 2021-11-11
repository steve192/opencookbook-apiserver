package com.sterul.opencookbookapiserver.services;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.entities.account.User;
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

    public List<Recipe> getRecipesByOwner(User owner) {
        return recipeRepository.findByOwner(owner);
    }

    public void deleteRecipe(Long id) {
        recipeRepository.deleteById(id);
    }

    public boolean hasAccessPermissionToRecipe(Long recipeId, User user) {
        var recipe = recipeRepository.getOne(recipeId);
        return recipe.getOwner().getUserId() == user.getUserId();
    }

}
