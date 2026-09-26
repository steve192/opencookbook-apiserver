package com.sterul.opencookbookapiserver.controllers.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminIngredientResponse;
import com.sterul.opencookbookapiserver.controllers.nutrition.IngredientLinkRequest;
import com.sterul.opencookbookapiserver.services.nutrition.linking.IngredientLinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Ingredients", description = "Admin ingredient api")
@ConditionalOnNutritionEnabled
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminIngredientLinkController {

    private final IngredientLinkService linkService;

    public AdminIngredientLinkController(IngredientLinkService linkService) {
        this.linkService = linkService;
    }

    @Operation(summary = "Say what an ingredient is", description = "Automatic matching never overrides it.")
    @PutMapping("/api/v1/admin/ingredients/{id}/link")
    public AdminIngredientResponse link(@PathVariable Long id, @Valid @RequestBody IngredientLinkRequest request) {
        var ingredient = request.isExcluded()
                ? linkService.excludeByAdmin(id)
                : linkService.linkByAdmin(id, request.catalogueFoodId());
        return AdminIngredientResponse.fromEntity(ingredient);
    }
}
