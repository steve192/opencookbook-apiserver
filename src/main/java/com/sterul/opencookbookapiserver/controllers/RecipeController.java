package com.sterul.opencookbookapiserver.controllers;

import java.util.List;
import java.util.NoSuchElementException;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
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
        var user = getLoggedInUser();
        return recipeService.getRecipesByOwner(user);
    }

    @PostMapping("")
    public RecipeResponse newRecipe(@RequestBody RecipeRequest recipeRequest) {
        var newRecipe = requestToEntity(recipeRequest);
        newRecipe.setOwner(getLoggedInUser());
        if (newRecipe.getServings() <= 0) {
            newRecipe.setServings(1);
        }
        return entityToResponse(recipeService.createNewRecipe(newRecipe));
    }

    @GetMapping("/{id}")
    public RecipeResponse single(@PathVariable Long id) throws NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        return entityToResponse(recipeService.getRecipeById(id));
    }

    @PutMapping("/{id}")
    public RecipeResponse updateRecipe(@PathVariable Long id, @RequestBody RecipeRequest recipeUpdate)
            throws NoSuchElementException, NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        recipeUpdate.setId(id);
        return entityToResponse(recipeService.updateSingleRecipe(requestToEntity(recipeUpdate)));

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

    @GetMapping("/import/available-hosts")
    public List<String> getAvilableImportHosts() {
        return recipeImportService.getAvailableImportHosts();
    }

    private RecipeResponse entityToResponse(Recipe recipe) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .title(recipe.getTitle())
                .images(recipe.getImages())
                .neededIngredients(recipe.getNeededIngredients())
                .preparationSteps(recipe.getPreparationSteps())
                .recipeGroups(recipe.getRecipeGroups())
                .servings(recipe.getServings())
                .build();
    }

    private Recipe requestToEntity(RecipeRequest recipe) {
        return Recipe.builder()
                .id(recipe.getId())
                .title(recipe.getTitle())
                .images(recipe.getImages())
                .neededIngredients(recipe.getNeededIngredients())
                .preparationSteps(recipe.getPreparationSteps())
                .recipeGroups(recipe.getRecipeGroups())
                .servings(recipe.getServings())
                .build();
    }

}
