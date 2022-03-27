package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeService {

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    @Lazy
    private RecipeGroupService recipeGroupService;

    @Autowired
    private WeekplanService weekplanService;

    public Recipe createNewRecipe(Recipe newRecipe) {
        createMissingIngredients(newRecipe);
        createMissingRecipeGroup(newRecipe);

        return recipeRepository.save(newRecipe);
    }

    private void createMissingIngredients(Recipe recipe) {
        for (var ingredientNeed : recipe.getNeededIngredients()) {
            var ingredient = ingredientNeed.getIngredient();
            if (ingredient.getId() == null) {
                // Convenience api which creates ingredients
                ingredientNeed.setIngredient(ingredientService.createOrGetIngredient(ingredient));
            }
        }
    }

    private void createMissingRecipeGroup(Recipe recipe) {
        for (var recipeGroup : recipe.getRecipeGroups()) {
            if (recipeGroup.getId() == null) {
                // Convenience api which creates recipe groups
                recipeGroup.setOwner(recipe.getOwner());
                var createdRecipeGroup = recipeGroupService.createRecipeGroup(recipeGroup);
                recipeGroup.setId(createdRecipeGroup.getId());
            }
        }
    }

    public List<Recipe> getRecipesByOwner(User owner) {
        return recipeRepository.findByOwner(owner);
    }

    public void deleteRecipe(Long id) {
        var weekplanDays = weekplanService.getWeekplanDaysByRecipe(id);
        for (var weekplanDay : weekplanDays) {
            var iterator = weekplanDay.getRecipes().iterator();
            while (iterator.hasNext()) {
                var recipe = iterator.next();
                if (recipe.getRecipe() != null && recipe.getRecipe().getId().equals(id)) {
                    iterator.remove();
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

            createMissingIngredients(recipeUpdate);
            createMissingRecipeGroup(recipeUpdate);

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
