package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.services.BringExportService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/bringexport")
@Tag(name = "Bring Export", description = "Data for bring ingredient export")
public class BringExportController extends BaseController {

    private BringExportService bringExportService;
    private RecipeService recipeService;

    public BringExportController(BringExportService bringExportService, RecipeService recipeService) {
        this.bringExportService = bringExportService;
        this.recipeService = recipeService;
    }

    @Operation(summary = "Get the bring export data for a given export id")
    @GetMapping
    public ResponseEntity<String> getExportData(@RequestParam String exportId)
            throws ElementNotFound {
        var export = bringExportService.getBringExport(exportId);
        var stringBuilder = new StringBuilder();
        stringBuilder.append("<div itemType='http://schema.org/Recipe'>");
        stringBuilder.append("<span itemProp='yield'>" + export.getBaseAmount() + "</span>");
        stringBuilder.append("<h1 itemProp='name'>Cookpal Import</h1>");
        stringBuilder.append("<img itemprop=\"image\" src=\"favicon.ico\"/>");
        stringBuilder.append("<ul>");
        export.getIngredients().forEach(ingredient -> {
            stringBuilder.append("<li itemProp='ingredients'>" + ingredient + "</li>");
        });
        stringBuilder.append("</ul>");
        stringBuilder.append("</div>");

        return ResponseEntity.ok(stringBuilder.toString());
    }

    @Operation(summary = "Create a bring export", description = "Creates a bring export for the given recipe. The logged in user must be owner. Exports are valid for 5 minutes")
    @PostMapping
    public ResponseEntity<ExportCreationResponse> createBringExport(
            @RequestBody ExportCreationRequest request)
            throws ElementNotFound {
        var user = this.getLoggedInUser();
        if (!recipeService.hasAccessPermissionToRecipe(request.recipeId(), user)) {
            // "Not found" rather than "not allowed": see RecipeController.requireOwnRecipe.
            throw new ElementNotFound();
        }
        var createdExport = bringExportService.createBringExport(request.recipeId(), user);
        return ResponseEntity.ok(new ExportCreationResponse(createdExport.getId()));
    }

    public record ExportCreationRequest(Long recipeId) {
    }

    public record ExportCreationResponse(String exportId) {

    }

    public record BringExportData(int baseAmount, List<String> ingredients) {

    }

}
