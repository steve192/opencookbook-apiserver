package com.sterul.opencookbookapiserver.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/**
 * Replaces a client's references by what the owner has: ingredients by name, groups and images by id.
 * A group or image of somebody else is not found.
 */
@Component
@Slf4j
public class RecipeReferenceResolver {

    private final IngredientService ingredientService;
    private final RecipeGroupRepository recipeGroupRepository;
    private final RecipeImageRepository recipeImageRepository;

    public RecipeReferenceResolver(IngredientService ingredientService,
            RecipeGroupRepository recipeGroupRepository, RecipeImageRepository recipeImageRepository) {
        this.ingredientService = ingredientService;
        this.recipeGroupRepository = recipeGroupRepository;
        this.recipeImageRepository = recipeImageRepository;
    }

    public void resolve(Recipe recipe, CookpalUser owner) throws ElementNotFound {
        resolveIngredients(recipe, owner);
        recipe.setRecipeGroups(resolveGroups(recipe.getRecipeGroups(), owner));
        recipe.setImages(resolveImages(recipe.getImages(), owner));
    }

    private void resolveIngredients(Recipe recipe, CookpalUser owner) {
        for (var need : recipe.getNeededIngredients()) {
            need.setIngredient(ingredientService.createOrGetIngredient(need.getIngredient(), owner));
        }
    }

    private List<RecipeGroup> resolveGroups(List<RecipeGroup> groups, CookpalUser owner) throws ElementNotFound {
        var resolved = new ArrayList<RecipeGroup>();
        for (var group : groups) {
            resolved.add(group.getId() == null ? createGroup(group.getTitle(), owner) : ownedGroup(group.getId(), owner));
        }
        return resolved;
    }

    private RecipeGroup createGroup(String title, CookpalUser owner) {
        log.info("Creating recipe group {} for user {}", title, owner.getUserId());
        return recipeGroupRepository.save(RecipeGroup.builder().title(title).owner(owner).build());
    }

    private RecipeGroup ownedGroup(Long id, CookpalUser owner) throws ElementNotFound {
        return recipeGroupRepository.findByIdAndOwner(id, owner).orElseThrow(ElementNotFound::new);
    }

    private List<RecipeImage> resolveImages(List<RecipeImage> images, CookpalUser owner) throws ElementNotFound {
        var resolved = new ArrayList<RecipeImage>();
        for (var image : images) {
            resolved.add(recipeImageRepository.findByUuidAndOwner(image.getUuid(), owner)
                    .orElseThrow(ElementNotFound::new));
        }
        return resolved;
    }
}
