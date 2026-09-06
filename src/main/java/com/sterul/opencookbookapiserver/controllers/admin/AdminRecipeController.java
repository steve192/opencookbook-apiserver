package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminRecipeRequest;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminRecipeResponse;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.RecipeService.RecipeDetails;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/recipes")
@Tag(name = "Recipes", description = "Admin recipe api")
@Slf4j
public class AdminRecipeController {

    private final RecipeService recipeService;

    public AdminRecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @Operation(summary = "Every recipe on this instance, with who owns it")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminRecipeResponse> getAll() {
        log.info("Admin: Accessing all recipes");
        return recipeService.getAllRecipes().stream().map(AdminRecipeResponse::fromEntity).toList();
    }

    @Operation(summary = "One recipe")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminRecipeResponse getOne(@PathVariable Long id) throws ElementNotFound {
        return AdminRecipeResponse.fromEntity(recipeService.getRecipeById(id));
    }

    @Operation(summary = "Correct a recipe",
            description = "Every detail given replaces the one that was there, so leaving one "
                    + "out clears it. Ingredients and images are left alone.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminRecipeResponse updateRecipe(@PathVariable Long id, @Valid @RequestBody AdminRecipeRequest request)
            throws ElementNotFound {
        log.info("Admin: Updating recipe {}", id);
        var updated = recipeService.updateRecipeDetails(id, new RecipeDetails(
                request.getTitle(),
                request.getServings() == null ? 0 : request.getServings(),
                request.getPreparationTime(),
                request.getTotalTime(),
                request.getRecipeType(),
                request.getPreparationSteps()));
        return AdminRecipeResponse.fromEntity(updated);
    }

    @Operation(summary = "Delete a recipe", description = "Takes its images and any share of it with it.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipe(@PathVariable Long id) throws ElementNotFound {
        log.info("Admin: Deleting recipe {}", id);
        recipeService.deleteRecipe(id);
    }
}
