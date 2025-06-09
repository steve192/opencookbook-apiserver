package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminIngredientRequest;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/v1/admin/ingredients")
@Tag(name = "Users", description = "Admin ingredient api")
@Slf4j
public class AdminIngredientsController {

    private final ActivationLinkRepository activationLinkRepository;

    private IngredientService ingredientService;

    public AdminIngredientsController(IngredientService ingredientService, ActivationLinkRepository activationLinkRepository) {
        this.ingredientService = ingredientService;
        this.activationLinkRepository = activationLinkRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<Ingredient> getAll(@RequestParam(required = false) boolean publicOnly) {
        log.info("Admin: Accessing all ingredients");
        if (publicOnly) {
            return ingredientService.getPublicIngredients();
        }
        return ingredientService.getAllIngredients();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Ingredient createPublicIngredient(@RequestBody AdminIngredientRequest ingredient) {
        log.info("Admin: Creating public ingredient");
        return ingredientService.createPublicIngredient(requestToEntity(ingredient));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Ingredient updatePublicIngredient(@PathVariable Long id, @RequestBody AdminIngredientRequest entity) throws ElementNotFound {
        log.info("Admin: Updating public ingredient");
        var newIngredient = requestToEntity(entity);
        return ingredientService.updateIngredient(newIngredient);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public void deletePublicIngredient(@PathVariable Long id) throws ElementNotFound {
        log.info("Admin: Deleting public ingredient");
        var ingredient = ingredientService.getIngredient(id);
        ingredientService.deleteIngredient(ingredient);
    }

    private Ingredient requestToEntity(AdminIngredientRequest ingredient) {
        return Ingredient.builder()
                .name(ingredient.getName())
                .id(ingredient.getId())
                .alternativeNames(ingredient.getAlternativeNames())
                .nutrientsEnergy(ingredient.getNutrientsEnergy())
                .nutrientsFat(ingredient.getNutrientsFat())
                .nutrientsSaturatedFat(ingredient.getNutrientsSaturatedFat())
                .nutrientsCarbohydrates(ingredient.getNutrientsCarbohydrates())
                .nutrientsSugar(ingredient.getNutrientsSugar())
                .nutrientsProtein(ingredient.getNutrientsProtein())
                .nutrientsSalt(ingredient.getNutrientsSalt())
                .build();
    }

}
