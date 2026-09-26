package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

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

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminCustomFoodRequest;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminCatalogueFoodResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminCatalogueFoodSummary;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminNutritionDatasetResponse;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueService;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/admin/catalogue")
@Tag(name = "Nutrition catalogue", description = "Foods ingredients link to for their nutrients")
@ConditionalOnNutritionEnabled
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminCatalogueController {

    private final CatalogueService catalogueService;
    private final NutritionDatasetReader datasetReader;

    public AdminCatalogueController(CatalogueService catalogueService, NutritionDatasetReader datasetReader) {
        this.catalogueService = catalogueService;
        this.datasetReader = datasetReader;
    }

    public record NameRequest(@NotBlank String languageIsoCode, @NotBlank String name) {
    }

    public record MergeRequest(@NotNull Long targetId) {
    }

    /** @param dietClass null puts the food back to unclassified */
    public record DietClassRequest(Diet dietClass) {
    }

    @Operation(summary = "Every catalogue food, as list rows")
    @GetMapping("/foods")
    public List<AdminCatalogueFoodSummary> getFoods() {
        return catalogueService.getAllFoods().stream().map(AdminCatalogueFoodSummary::fromEntity).toList();
    }

    @Operation(summary = "One catalogue food")
    @GetMapping("/foods/{id}")
    public AdminCatalogueFoodResponse getFood(@PathVariable Long id) {
        return response(catalogueService.getFood(id));
    }

    @Operation(summary = "Add a custom food")
    @PostMapping("/foods")
    public AdminCatalogueFoodResponse createFood(@Valid @RequestBody AdminCustomFoodRequest request) {
        return response(catalogueService.createCustomFood(request.toCustomFood()));
    }

    @Operation(summary = "Correct a custom food", description = "Foods shipped with the dataset are read-only.")
    @PutMapping("/foods/{id}")
    public AdminCatalogueFoodResponse updateFood(@PathVariable Long id, @Valid @RequestBody AdminCustomFoodRequest request) {
        return response(catalogueService.updateCustomFood(id, request.toCustomFood()));
    }

    @Operation(summary = "Delete a custom food", description = "Only while no ingredient links to it and no food is its variant.")
    @DeleteMapping("/foods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFood(@PathVariable Long id) {
        catalogueService.deleteCustomFood(id);
    }

    @Operation(summary = "Add a name to a food", description = "Also for dataset foods; the name must not belong to another food.")
    @PostMapping("/foods/{id}/names")
    public AdminCatalogueFoodResponse addName(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return response(catalogueService.addName(id, request.languageIsoCode(), request.name()));
    }

    @Operation(summary = "Remove a name an administrator added")
    @DeleteMapping("/foods/{id}/names")
    public AdminCatalogueFoodResponse removeName(@PathVariable Long id, @RequestParam String languageIsoCode,
            @RequestParam String name) {
        return response(catalogueService.removeName(id, languageIsoCode, name));
    }

    @Operation(summary = "Correct what a food counts as for a diet",
            description = "Allowed for dataset foods too: the shipped class is read from a description, and the "
                    + "correction is marked as an administrator's so later dataset releases keep it.")
    @PutMapping("/foods/{id}/diet-class")
    public AdminCatalogueFoodResponse classify(@PathVariable Long id, @Valid @RequestBody DietClassRequest request) {
        return response(catalogueService.classifyByAdmin(id, request.dietClass()));
    }

    @Operation(summary = "Merge a custom food into another food",
            description = "Ingredients linked to the custom food move to the target, its names too; the custom food is deleted.")
    @PostMapping("/foods/{id}/merge")
    public AdminCatalogueFoodResponse merge(@PathVariable Long id, @Valid @RequestBody MergeRequest request) {
        return response(catalogueService.mergeCustomFood(id, request.targetId()));
    }

    @Operation(summary = "The shipped dataset and its imports into this instance")
    @GetMapping("/dataset")
    public AdminNutritionDatasetResponse getDataset() {
        return AdminNutritionDatasetResponse.of(datasetReader.manifest(), catalogueService.getImports());
    }

    private AdminCatalogueFoodResponse response(CatalogueFood food) {
        return AdminCatalogueFoodResponse.fromEntity(food, catalogueService.countLinkedIngredients(food));
    }
}
