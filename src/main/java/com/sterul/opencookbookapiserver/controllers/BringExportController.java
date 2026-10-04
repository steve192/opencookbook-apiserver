package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.sterul.opencookbookapiserver.controllers.shopping.requests.ShownLineRequest;
import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.services.BringExportService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/bringexport")
@Tag(name = "Bring Export", description = "Data for bring ingredient export")
public class BringExportController extends BaseController {

    private static final String DEFAULT_TITLE = "CookPal Import";

    /** Served unauthenticated on the app's origin, so nothing on it may run. */
    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; img-src 'self'";

    private final BringExportService bringExportService;
    private final RecipeService recipeService;
    private final ShoppingImportService shoppingImports;

    public BringExportController(BringExportService bringExportService, RecipeService recipeService,
            ShoppingImportService shoppingImports, SignedInUserService signedInUser) {
        super(signedInUser);
        this.bringExportService = bringExportService;
        this.recipeService = recipeService;
        this.shoppingImports = shoppingImports;
    }

    @Operation(summary = "Get the bring export data for a given export id")
    @GetMapping
    public ResponseEntity<String> getExportData(@RequestParam String exportId) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .body(pageOf(bringExportService.getBringExport(exportId)));
    }

    @Operation(summary = "Create a bring export", description = "Creates a bring export for a recipe "
            + "the caller may read - their own, or one a household makes readable. Exports are valid "
            + "for 5 minutes and are fetched without authentication, so they carry ingredient lines only.")
    @PostMapping
    public ResponseEntity<ExportCreationResponse> createBringExport(
            @RequestBody ExportCreationRequest request) {
        var user = this.getLoggedInUser();
        var recipe = recipeService.getRecipeFor(request.recipeId(), user);
        var createdExport = bringExportService.createBringExport(recipe, user);
        return ResponseEntity.ok(new ExportCreationResponse(createdExport.getId()));
    }

    @Operation(summary = "Create a bring export from finished shopping lines",
            description = "For a week or a recipe put together in the app's import sheet. Also records "
                    + "which offered lines were left out, which is how staples are learned.")
    @PostMapping("/lines")
    public ExportCreationResponse createBringExportOfLines(@Valid @RequestBody LinesExportRequest request) {
        var export = shoppingImports.toBring(request.title(), request.servings(), request.lines(),
                request.shown().stream().map(ShownLineRequest::toObservation).toList(), getLoggedInUser());
        return new ExportCreationResponse(export.getId());
    }

    private static String pageOf(BringExport export) {
        var page = new StringBuilder();
        page.append("<div itemType='http://schema.org/Recipe'>");
        page.append("<span itemProp='yield'>").append(export.getBaseAmount()).append("</span>");
        page.append("<h1 itemProp='name'>").append(escape(titleOf(export))).append("</h1>");
        page.append("<img itemprop=\"image\" src=\"favicon.ico\"/>");
        page.append("<ul>");
        export.getIngredients().forEach(line -> page.append("<li itemProp='ingredients'>").append(escape(line))
                .append("</li>"));
        page.append("</ul>");
        page.append("</div>");
        return page.toString();
    }

    private static String titleOf(BringExport export) {
        return export.getTitle() == null || export.getTitle().isBlank() ? DEFAULT_TITLE : export.getTitle();
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text);
    }

    public record ExportCreationRequest(Long recipeId) {
    }

    public record LinesExportRequest(
            @NotBlank @Size(max = 80) String title,
            @Min(1) @Max(100) int servings,
            @NotEmpty @Size(max = 300) List<@NotBlank @Size(max = 200) String> lines,
            @NotNull @Size(max = 300) List<@Valid ShownLineRequest> shown) {
    }

    public record ExportCreationResponse(String exportId) {
    }
}
