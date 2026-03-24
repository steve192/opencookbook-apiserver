package com.sterul.opencookbookapiserver.services;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.RecipeShare;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeShareRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@Service
public class RecipeSharingService {

    private final RecipeShareRepository recipeShareRepository;

    public RecipeSharingService(RecipeShareRepository recipeShareRepository) {
        this.recipeShareRepository = recipeShareRepository;
    }

    public RecipeShare getRecipeShareByShareId(String shareId, boolean incrementAccessCount) throws ElementNotFound {
        var recipeShare = recipeShareRepository.findById(shareId)
                .orElseThrow(() -> new ElementNotFound());
        if (incrementAccessCount) {

            recipeShare.setAccessCount(recipeShare.getAccessCount() + 1);
            recipeShareRepository.save(recipeShare);
        }
        return recipeShare;
    }

    public RecipeShare shareRecipePublicly(Recipe recipe, CookpalUser user) {
        var recipeShares = recipeShareRepository.findByRecipe(recipe);
        if (!recipeShares.isEmpty()) {
            return recipeShares.get(0);
        }

        return recipeShareRepository.save(RecipeShare.builder()
                .recipe(recipe)
                .owner(user)
                .accessCount(0L)
                .build());
    }

    public void unshareRecipePublicly(Recipe recipe) {
        recipeShareRepository.deleteByRecipe(recipe);
    }
}
