package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.IngredientService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/ingredients")
@Tag(name = "Users", description = "Admin ingredient api")
@Slf4j
public class AdminIngredientsController {

    private IngredientService ingredientService;

    public AdminIngredientsController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<Ingredient> getAll() {
        log.info("Admin: Accessing all ingredients");
        return ingredientService.getAllIngredients();
    }
}
