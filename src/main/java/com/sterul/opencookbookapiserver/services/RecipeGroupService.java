package com.sterul.opencookbookapiserver.services;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeGroupService {

    @Autowired
    private RecipeGroupRepository recipeGroupRepository;

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
        recipeGroupRepository.deleteById(recipeGroupId);
    }

    public boolean hasAccessPermissionToRecipeGroup(Long recipeGroupId, User user) {
        var recipeGroup = recipeGroupRepository.findById(recipeGroupId).get();
        return recipeGroup.getOwner().getUserId().equals(user.getUserId());
    }

    public List<RecipeGroup> getRecipeGroupsByOwner(User owner) {
        return recipeGroupRepository.findByOwner(owner);
    }
}
