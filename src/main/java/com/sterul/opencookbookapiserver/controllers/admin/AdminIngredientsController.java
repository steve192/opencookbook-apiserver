package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminIngredientRequest;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminIngredientResponse;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientAlternativeNames;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/ingredients")
@Tag(name = "Ingredients", description = "Admin ingredient api")
@Slf4j
public class AdminIngredientsController {

    private final IngredientService ingredientService;

    public AdminIngredientsController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @Operation(summary = "Every ingredient, public and privately owned")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminIngredientResponse> getAll(@RequestParam(required = false) Boolean publicOnly) {
        log.info("Admin: Accessing all ingredients");
        var ingredients = Boolean.TRUE.equals(publicOnly)
                ? ingredientService.getPublicIngredients()
                : ingredientService.getAllIngredients();
        return ingredients.stream().map(AdminIngredientResponse::fromEntity).toList();
    }

    @Operation(summary = "One ingredient")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminIngredientResponse getOne(@PathVariable Long id) throws ElementNotFound {
        return AdminIngredientResponse.fromEntity(ingredientService.getIngredient(id));
    }

    @Operation(summary = "Add a public ingredient")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminIngredientResponse createPublicIngredient(
            @Valid @RequestBody AdminIngredientRequest ingredient) {
        log.info("Admin: Creating public ingredient");
        return AdminIngredientResponse.fromEntity(
                ingredientService.createPublicIngredient(requestToEntity(ingredient)));
    }

    @Operation(summary = "Correct an ingredient",
            description = "Whether it is public and who owns it are not an operator's to change.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminIngredientResponse updateIngredient(@PathVariable Long id,
            @Valid @RequestBody AdminIngredientRequest request) throws ElementNotFound {
        log.info("Admin: Updating ingredient {}", id);
        var ingredient = requestToEntity(request);
        // The path decides, not the body.
        ingredient.setId(id);
        return AdminIngredientResponse.fromEntity(ingredientService.updateIngredient(ingredient));
    }

    @Operation(summary = "Delete an ingredient")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIngredient(@PathVariable Long id) throws ElementNotFound {
        log.info("Admin: Deleting ingredient {}", id);
        ingredientService.deleteIngredient(ingredientService.getIngredient(id));
    }

    private Ingredient requestToEntity(AdminIngredientRequest ingredient) {
        return Ingredient.builder()
                .name(ingredient.getName())
                .additionalInfo(ingredient.getAdditionalInfo())
                .alternativeNames(alternativeNames(ingredient))
                .nutrientsEnergy(ingredient.getNutrientsEnergy())
                .nutrientsFat(ingredient.getNutrientsFat())
                .nutrientsSaturatedFat(ingredient.getNutrientsSaturatedFat())
                .nutrientsCarbohydrates(ingredient.getNutrientsCarbohydrates())
                .nutrientsSugar(ingredient.getNutrientsSugar())
                .nutrientsProtein(ingredient.getNutrientsProtein())
                .nutrientsSalt(ingredient.getNutrientsSalt())
                .build();
    }

    private List<IngredientAlternativeNames> alternativeNames(AdminIngredientRequest ingredient) {
        if (ingredient.getAlternativeNames() == null) {
            return new ArrayList<>();
        }
        return ingredient.getAlternativeNames().stream()
                .map(name -> IngredientAlternativeNames.builder()
                        .id(name.getId())
                        .languageIsoCode(name.getLanguageIsoCode())
                        .alternativeName(name.getAlternativeName())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
