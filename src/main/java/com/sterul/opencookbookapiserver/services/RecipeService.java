package com.sterul.opencookbookapiserver.services;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeService {

    @Autowired
    IngredientRepository ingredientRepository;

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    RecipeGroupService recipeGroupService;

    @Autowired
    WeekplanService weekplanService;

    public Recipe createNewRecipe(Recipe newRecipe) {
        for (var ingredientNeed : newRecipe.getNeededIngredients()) {
            var ingredient = ingredientNeed.getIngredient();
            if (ingredient.getId() == null) {
                // Convenience api which creates ingredients
                ingredientNeed.setIngredient(ingredientRepository.save(ingredient));
            }
        }

        for (var recipeGroup : newRecipe.getRecipeGroups()) {
            if (recipeGroup.getId() == null) {
                // Convenience api which creates recipe groups
                recipeGroup.setOwner(newRecipe.getOwner());
                var createdRecipeGroup = recipeGroupService.createRecipeGroup(recipeGroup);
                recipeGroup.setId(createdRecipeGroup.getId());
            }
        }
        return recipeRepository.save(newRecipe);
    }

    public List<Recipe> getRecipesByOwner(User owner) {
        return recipeRepository.findByOwner(owner);
    }

    public void deleteRecipe(Long id) {
        var weekplanDays = weekplanService.getWeekplanDaysByRecipe(id);
        for (var weekplanDay : weekplanDays) {
            for (var recipe : weekplanDay.getRecipes()) {
                if (recipe.getId().equals(id)) {
                    weekplanDay.getRecipes().remove(recipe);
                }
            }
            weekplanService.updateWeekplanDay(weekplanDay);
        }
        recipeRepository.deleteById(id);
    }

    public boolean hasAccessPermissionToRecipe(Long recipeId, User user) throws ElementNotFound {
        var recipe = recipeRepository.findById(recipeId);
        if (!recipe.isPresent()) {
            throw new ElementNotFound();
        }
        return recipe.get().getOwner().getUserId().equals(user.getUserId());
    }

    public List<Recipe> getRecipesByRecipeGroup(RecipeGroup recipeGroup) {
        return recipeRepository.findByRecipeGroups(recipeGroup);
    }

    public Recipe updateSingleRecipe(Recipe recipeUpdate) {
        return recipeRepository.findById(recipeUpdate.getId()).map(existingRecipe -> {
            recipeUpdate.setId(existingRecipe.getId());
            recipeUpdate.setOwner(existingRecipe.getOwner());

            for (var recipeGroup : recipeUpdate.getRecipeGroups()) {
                if (recipeGroup.getId() == null) {
                    // Convenience api which creates recipe groups
                    recipeGroup.setOwner(recipeUpdate.getOwner());
                    var createdRecipeGroup = recipeGroupService.createRecipeGroup(recipeGroup);
                    recipeGroup.setId(createdRecipeGroup.getId());
                }
            }
            return recipeRepository.save(recipeUpdate);
        }).orElseThrow();
    }

    public Recipe getRecipeById(Long id) throws ElementNotFound {
        var recipe = recipeRepository.findById(id);
        if (!recipe.isPresent()) {
            throw new ElementNotFound();
        }
        return recipe.get();
    }

}
