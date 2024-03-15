package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/recipes")
@Tag(name = "Users", description = "Admin recipe api")
@Slf4j
public class AdminRecipeController {

    private RecipeService recipeService;

    public AdminRecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<Recipe> getAll() {
        log.info("Admin: Accessing all recipes");
        return recipeService.getAllRecipes();
    }
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<Recipe> getCount() {
        log.info("Admin: Accessing recipe count");
        return recipeService.getAllRecipes();
    }


}
