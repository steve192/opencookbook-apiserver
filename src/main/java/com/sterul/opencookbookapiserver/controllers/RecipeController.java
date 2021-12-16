package com.sterul.opencookbookapiserver.controllers;

import java.util.List;
import java.util.NoSuchElementException;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeImportService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController extends BaseController {

    @Autowired
    private RecipeImportService recipeImportService;

    @Autowired
    private RecipeService recipeService;

    @GetMapping("")
    public List<Recipe> searchRecipe(@RequestParam(required = false) String searchString) {
        // userRepository.findByEmailAddress()
        // recipeService.getRecipesByOwner()
        var user = getLoggedInUser();
        return recipeService.getRecipesByOwner(user);
    }

    @PostMapping("")
    public Recipe newRecipe(@RequestBody Recipe newRecipe) {
        newRecipe.setOwner(getLoggedInUser());
        if (newRecipe.getServings() <= 0) {
            newRecipe.setServings(1);
        }
        return recipeService.createNewRecipe(newRecipe);
    }

    @GetMapping("/{id}")
    public Recipe single(@PathVariable Long id) throws NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        return recipeService.getRecipeById(id);
    }

    @PutMapping("/{id}")
    public Recipe updateRecipe(@PathVariable Long id, @RequestBody Recipe recipeUpdate)
            throws NoSuchElementException, NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        return recipeService.updateSingleRecipe(recipeUpdate);

    }

    @DeleteMapping("/{id}")
    public void deleteRecipe(@PathVariable Long id) throws NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        recipeService.deleteRecipe(id);
    }

    @GetMapping("/import")
    public Recipe importRecipe(@RequestParam String importUrl)
            throws ImportNotSupportedException, RecipeImportFailedException {
        var owner = getLoggedInUser();
        return recipeImportService.importRecipe(importUrl, owner);
    }

}
