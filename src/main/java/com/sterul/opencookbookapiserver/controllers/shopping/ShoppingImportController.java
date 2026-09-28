package com.sterul.opencookbookapiserver.controllers.shopping;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ImportPreviewResponse;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.shopping.preview.ImportPreviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ShoppingPaths.BASE + "/preview")
@Tag(name = "Shopping import", description = "What to offer when putting meals on a shopping list")
public class ShoppingImportController extends BaseController {

    /** Longer than any week a sheet shows, short enough that one request stays cheap. */
    private static final int MAX_DAYS = 31;

    private final ImportPreviewService previews;
    private final PlanScopes planScopes;

    public ShoppingImportController(ImportPreviewService previews, PlanScopes planScopes,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.previews = previews;
        this.planScopes = planScopes;
    }

    @Operation(summary = "The meals of one plan in a date range, with their ingredients",
            description = "Your own plan, or the household's given. Recipes default to the plan's household size.")
    @GetMapping("/week")
    public ImportPreviewResponse week(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) String household) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "A range of up to " + MAX_DAYS + " days is needed");
        }
        var user = getLoggedInUser();
        return ImportPreviewResponse.of(previews.forWeek(planScopes.of(user, household), from, to, user));
    }

    @Operation(summary = "One recipe you can read, with its ingredients")
    @GetMapping("/recipe/{recipeId}")
    public ImportPreviewResponse recipe(@PathVariable Long recipeId) {
        return ImportPreviewResponse.of(List.of(previews.forRecipe(recipeId, getLoggedInUser())));
    }
}
