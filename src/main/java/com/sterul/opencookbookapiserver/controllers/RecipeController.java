package com.sterul.opencookbookapiserver.controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.requests.IngredientNeedRequest;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.controllers.support.RecipeResponses;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.RecipeImportService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/recipes")
@Tag(name = "Recipes", description = "Creating, chaning, deleting, importing recipes")
public class RecipeController extends BaseController {

    private final RecipeImportService recipeImportService;
    private final RecipeService recipeService;
    private final RecipeResponses recipeResponses;

    public RecipeController(RecipeImportService recipeImportService, RecipeService recipeService, RecipeResponses recipeResponses) {
        this.recipeImportService = recipeImportService;
        this.recipeService = recipeService;
        this.recipeResponses = recipeResponses;
    }

    @Operation(summary = "Search or get recipes")
    @GetMapping("")
    public List<RecipeResponse> searchRecipe(@RequestParam(required = false) String searchString,
            @RequestParam(required = false) List<Diet> categories) {
        var user = getLoggedInUser();
        return recipeService.searchUserRecipes(user, searchString, categories).stream()
                .map(recipeResponses::of)
                .toList();
    }

    @Operation(summary = "Create a new recipe", description = "Not existing ingredients and recipe groups will be created when no id is supplied.")
    @PostMapping("")
    public RecipeResponse newRecipe(@RequestBody @Valid RecipeRequest recipeRequest) throws ElementNotFound {
        var newRecipe = requestToEntity(null, recipeRequest);
        newRecipe.setOwner(getLoggedInUser());
        if (newRecipe.getServings() <= 0) {
            newRecipe.setServings(1);
        }
        return recipeResponses.of(recipeService.createNewRecipe(newRecipe));
    }

    @Operation(summary = "Get a single recipe")
    @GetMapping("/{id}")
    public RecipeResponse single(@PathVariable Long id) throws ElementNotFound {
        requireOwnRecipe(id);
        return recipeResponses.of(recipeService.getRecipeById(id));
    }

    @Operation(summary = "Update an existing recipe")
    @PutMapping("/{id}")
    public RecipeResponse updateRecipe(@PathVariable Long id, @RequestBody @Valid RecipeRequest recipeUpdate)
            throws ElementNotFound {
        requireOwnRecipe(id);
        return recipeResponses.of(recipeService.updateSingleRecipe(requestToEntity(id, recipeUpdate)));
    }

    @Operation(summary = "Delete a recipe", description = "Deleted recipes will automatically deleted from weekplan days. Also linked images will be automatically deleted")
    @DeleteMapping("/{id}")
    public void deleteRecipe(@PathVariable Long id) throws ElementNotFound {
        requireOwnRecipe(id);
        recipeService.deleteRecipe(id);
    }

    @Operation(summary = "Import a recipe from a recipe website")
    @GetMapping("/import")
    public RecipeResponse importRecipe(@RequestParam String importUrl)
            throws ApiException {
        var owner = getLoggedInUser();
        return recipeResponses.of(recipeImportService.importRecipe(importUrl, owner));
    }

    @Operation(summary = "Get a list of supported websites", description = "Additional websites are supported by a generic import. Quality can vary")
    @GetMapping("/import/available-hosts")
    public List<String> getAvilableImportHosts() {
        return recipeImportService.getAvailableImportHosts();
    }

    /**
     * Refuses with "not found" rather than "not allowed", deliberately: answering differently
     * for a recipe that exists but belongs to somebody else would let anyone count the recipes
     * on this server by walking the ids.
     */
    private void requireOwnRecipe(Long id) throws ElementNotFound {
        if (!recipeService.hasAccessPermissionToRecipe(id, getLoggedInUser())) {
            throw new ElementNotFound();
        }
    }

    /** Builds unresolved references for {@link RecipeService}; lines are always new. */
    private Recipe requestToEntity(Long id, RecipeRequest recipe) {
        return Recipe.builder()
                .id(id)
                .title(recipe.getTitle())
                .images(recipe.getImages().stream()
                        .map(image -> RecipeImage.builder().uuid(image.getUuid()).build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .neededIngredients(recipe.getNeededIngredients().stream()
                        .filter(need -> !need.isEmpty())
                        .map(this::needToEntity)
                        .collect(Collectors.toCollection(ArrayList::new)))
                .preparationSteps(recipe.getPreparationSteps())
                .recipeGroups(
                        recipe.getRecipeGroups().stream().map(recipeGroup -> RecipeGroup.builder()
                                .id(recipeGroup.getId())
                                .title(recipeGroup.getTitle())
                                .build())
                                .collect(Collectors.toCollection(ArrayList::new)))
                .servings(recipe.getServings())
                .preparationTime(recipe.getPreparationTime())
                .totalTime(recipe.getTotalTime())
                .recipeType(recipe.getRecipeType())
                .mealTypes(new HashSet<>(recipe.getMealTypes()))
                .dishRole(recipe.getDishRole())
                .build();
    }

    private IngredientNeed needToEntity(IngredientNeedRequest need) {
        return IngredientNeed.builder()
                .amount(need.getAmount())
                .unit(need.getUnit())
                .ingredient(Ingredient.builder().name(need.getIngredient().getName()).build())
                .build();
    }

}
