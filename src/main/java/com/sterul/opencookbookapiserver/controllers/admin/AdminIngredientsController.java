package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminIngredientResponse;
import com.sterul.opencookbookapiserver.services.IngredientService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/ingredients")
@Tag(name = "Ingredients", description = "Admin ingredient api")
@Slf4j
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminIngredientsController {

    private final IngredientService ingredientService;

    public AdminIngredientsController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @Operation(summary = "Every user's ingredients")
    @GetMapping
    public List<AdminIngredientResponse> getAll() {
        log.info("Admin: Accessing all ingredients");
        return ingredientService.getAllIngredients().stream().map(AdminIngredientResponse::fromEntity).toList();
    }

    @Operation(summary = "One ingredient")
    @GetMapping("/{id}")
    public AdminIngredientResponse getOne(@PathVariable Long id) {
        return AdminIngredientResponse.fromEntity(ingredientService.getIngredient(id));
    }

    @Operation(summary = "Delete an ingredient no recipe uses")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIngredient(@PathVariable Long id) {
        log.info("Admin: Deleting ingredient {}", id);
        ingredientService.deleteIngredient(ingredientService.getIngredient(id));
    }
}
