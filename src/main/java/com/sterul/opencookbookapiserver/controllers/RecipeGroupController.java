package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.services.RecipeGroupService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes/group")
public class RecipeGroupController extends BaseController {

    @Autowired
    RecipeGroupService recipeGroupService;

    @GetMapping("/")
    public List<RecipeGroup> getAll() {
        return recipeGroupService.getRecipeGroupsByOwner(getLoggedInUser());
    }

    @PostMapping("/")
    public RecipeGroup create(@RequestBody RecipeGroup recipeGroup) {
        recipeGroup.setOwner(getLoggedInUser());
        return recipeGroupService.createRecipeGroup(recipeGroup);
    }

    @PutMapping("/{id}")
    public RecipeGroup change(@RequestParam Long id, @RequestBody RecipeGroup updatedRecipeGroup)
            throws NotAuthorizedException {

        if (!recipeGroupService.hasAccessPermissionToRecipeGroup(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        return recipeGroupService.updateRecipeGroup(updatedRecipeGroup);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestParam Long id) throws NotAuthorizedException {
        if (!recipeGroupService.hasAccessPermissionToRecipeGroup(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }

        recipeGroupService.deleteRecipeGroup(id);
    }
}
