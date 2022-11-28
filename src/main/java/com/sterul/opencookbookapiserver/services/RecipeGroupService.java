package com.sterul.opencookbookapiserver.services;

import java.util.List;
import java.util.NoSuchElementException;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class RecipeGroupService {

    @Autowired
    private RecipeGroupRepository recipeGroupRepository;

    @Autowired
    private RecipeService recipeService;

    public RecipeGroup createRecipeGroup(RecipeGroup recipeGroup) {
        log.info("Creating recipe group {} for user {}", recipeGroup.getTitle(), recipeGroup.getOwner());
        return recipeGroupRepository.save(recipeGroup);
    }

    public RecipeGroup updateRecipeGroup(RecipeGroup recipeGroupUpdate) {
        log.info("Updating recipe group {} for user {}", recipeGroupUpdate.getTitle(),
                recipeGroupUpdate.getOwner());
        return recipeGroupRepository.findById(recipeGroupUpdate.getId()).map(existingRecipeGroup -> {
            recipeGroupUpdate.setId(existingRecipeGroup.getId());
            recipeGroupUpdate.setOwner(existingRecipeGroup.getOwner());
            return recipeGroupRepository.save(recipeGroupUpdate);
        }).orElseThrow();
    }

    public void deleteRecipeGroup(Long recipeGroupId) {
        log.info("Deleting recipe group {} for user {}", recipeGroupId);
        var recipeGroupOptional = recipeGroupRepository.findById(recipeGroupId);
        if (recipeGroupOptional.isEmpty()) {
            log.warn("Recipe group does not exist");
            throw new NoSuchElementException("Receipe group " + recipeGroupId + " not existing. Cannot delete");
        }
        var recipesToUnassignGroup = recipeService.getRecipesByRecipeGroup(recipeGroupOptional.get());

        // TODO: Replace clean with specific group if multi groups are implemented
        recipesToUnassignGroup.forEach(recipe -> {
            log.info("Deleting recipe group from recipe {}", recipe.getId());
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
