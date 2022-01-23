package com.sterul.opencookbookapiserver.services;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeGroupService {

    @Autowired
    private RecipeGroupRepository recipeGroupRepository;

    @Autowired
    private RecipeService recipeService;

    public RecipeGroup createRecipeGroup(RecipeGroup recipeGroup) {
        return recipeGroupRepository.save(recipeGroup);
    }

    public RecipeGroup updateRecipeGroup(RecipeGroup recipeGroupUpdate) {
        return recipeGroupRepository.findById(recipeGroupUpdate.getId()).map(existingRecipeGroup -> {
            recipeGroupUpdate.setId(existingRecipeGroup.getId());
            return recipeGroupRepository.save(recipeGroupUpdate);
        }).orElseThrow();
    }

    public void deleteRecipeGroup(Long recipeGroupId) {
        var recipeGroupOptional = recipeGroupRepository.findById(recipeGroupId);
        if (recipeGroupOptional.isEmpty()) {
            throw new Error("Receipe group " + recipeGroupId + " not existing. Cannot delete");
        }
        var recipesToUnassignGroup = recipeService.getRecipesByRecipeGroup(recipeGroupOptional.get());

        // TODO: Replace clean with specific group if multi groups are implemented
        recipesToUnassignGroup.forEach(recipe -> {
            recipe.getRecipeGroups().clear();
            recipeService.updateSingleRecipe(recipe);
        });

        recipeGroupRepository.deleteById(recipeGroupId);
    }

    public boolean hasAccessPermissionToRecipeGroup(Long recipeGroupId, User user) throws ElementNotFound {
        var recipeGroup = recipeGroupRepository.findById(recipeGroupId);
        if (recipeGroup.isEmpty()) {
            throw new ElementNotFound();
        }
        return recipeGroup.get().getOwner().getUserId().equals(user.getUserId());
    }

    public List<RecipeGroup> getRecipeGroupsByOwner(User owner) {
        return recipeGroupRepository.findByOwner(owner);
    }
}
