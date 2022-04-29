package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeImportService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/recipes")
@Tag(name = "Recipes", description = "Creating, chaning, deleting, importing recipes")
public class RecipeController extends BaseController {

    @Autowired
    private RecipeImportService recipeImportService;

    @Autowired
    private RecipeService recipeService;

    @Operation(summary = "Search or get recipes")
    @GetMapping("")
    public List<RecipeResponse> searchRecipe(@RequestParam(required = false) String searchString, @RequestParam(required = false) List<Recipe.RecipeType> categories) {
        var user = getLoggedInUser();
        return recipeService.searchUserRecipes(user, searchString, categories).stream()
                .map(this::entityToResponse)
                .toList();
    }

    @Operation(summary = "Create a new recipe", description = "Not existing ingredients and recipe groups will be created when no id is supplied.")
    @PostMapping("")
    public RecipeResponse newRecipe(@RequestBody RecipeRequest recipeRequest) {
        var newRecipe = requestToEntity(recipeRequest);
        newRecipe.setOwner(getLoggedInUser());
        if (newRecipe.getServings() <= 0) {
            newRecipe.setServings(1);
        }
        return entityToResponse(recipeService.createNewRecipe(newRecipe));
    }

    @Operation(summary = "Get a single recipe")
    @GetMapping("/{id}")
    public RecipeResponse single(@PathVariable Long id) throws NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        return entityToResponse(recipeService.getRecipeById(id));
    }

    @Operation(summary = "Update an existing recipe")
    @PutMapping("/{id}")
    public RecipeResponse updateRecipe(@PathVariable Long id, @RequestBody RecipeRequest recipeUpdate)
            throws NoSuchElementException, NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        recipeUpdate.setId(id);
        return entityToResponse(recipeService.updateSingleRecipe(requestToEntity(recipeUpdate)));

    }

    @Operation(summary = "Delete a recipe", description = "Deleted recipes will automatically deleted from weekplan days. Also linked images will be automatically deleted")
    @DeleteMapping("/{id}")
    public void deleteRecipe(@PathVariable Long id) throws NotAuthorizedException, ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        recipeService.deleteRecipe(id);
    }

    @Operation(summary = "Import a recipe from a recipe website")
    @GetMapping("/import")
    public RecipeResponse importRecipe(@RequestParam String importUrl)
            throws ImportNotSupportedException, RecipeImportFailedException {
        var owner = getLoggedInUser();
        return entityToResponse(recipeImportService.importRecipe(importUrl, owner));
    }

    @Operation(summary = "Get a list of supported websites", description = "Additional websites are supported by a generic import. Quality can vary")
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
                .preparationTime(recipe.getPreparationTime())
                .totalTime(recipe.getTotalTime())
                .recipeType(recipe.getRecipeType())
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
                .preparationTime(recipe.getPreparationTime())
                .totalTime(recipe.getTotalTime())
                .recipeType(recipe.getRecipeType())
                .build();
    }

}
