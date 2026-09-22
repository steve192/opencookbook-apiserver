package com.sterul.opencookbookapiserver.controllers.households;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.households.ConditionalOnHouseholdsEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.households.responses.HouseholdRecipePageResponse;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.households.HouseholdCookbook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@ConditionalOnHouseholdsEnabled
@RequestMapping(HouseholdPaths.BASE + "/{householdId}/recipes")
@Tag(name = "Households", description = "Sharing a cookbook and a weekplan with the people you cook with")
public class HouseholdCookbookController extends BaseController {

    private final HouseholdCookbook cookbook;

    public HouseholdCookbookController(HouseholdCookbook cookbook) {
        this.cookbook = cookbook;
    }

    @Operation(summary = "The household's cookbook",
            description = "The recipes of every member who shares one with it, a page at a time: by title, "
                    + "or with a search the best matches first, matched the way the app searches its own cookbook.")
    @GetMapping
    public HouseholdRecipePageResponse getRecipes(@Valid @NotBlank @PathVariable String householdId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false) String search) throws ElementNotFound {
        var viewer = getLoggedInUser();
        return HouseholdRecipePageResponse.of(cookbook.recipesOf(householdId, viewer, page, search), viewer);
    }
}
