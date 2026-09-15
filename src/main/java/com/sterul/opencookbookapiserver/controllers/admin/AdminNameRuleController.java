package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminNameRuleResponse;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueNameRule;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueService;
import com.sterul.opencookbookapiserver.services.nutrition.linking.NameRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/admin/nutrition/name-rules")
@Tag(name = "Nutrition name rules", description = "Names never to link, or never to link to a food")
@ConditionalOnNutritionEnabled
public class AdminNameRuleController extends BaseController {

    private final NameRuleService nameRules;
    private final CatalogueService catalogueService;

    public AdminNameRuleController(NameRuleService nameRules, CatalogueService catalogueService) {
        this.nameRules = nameRules;
        this.catalogueService = catalogueService;
    }

    /** @param catalogueFoodId for NEVER_LINK_TO only */
    public record NameRuleRequest(@NotBlank String name, @NotNull CatalogueNameRule.Kind kind, Long catalogueFoodId) {

        @AssertTrue(message = "a food for NEVER_LINK_TO, none for NOT_A_FOOD")
        @Schema(hidden = true)
        public boolean isFoodGivenWhereNeeded() {
            return (kind == CatalogueNameRule.Kind.NEVER_LINK_TO) == (catalogueFoodId != null);
        }
    }

    @Operation(summary = "Every name rule, by name")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminNameRuleResponse> getRules() {
        return nameRules.getRules().stream().map(AdminNameRuleResponse::fromEntity).toList();
    }

    @Operation(summary = "Add a name rule")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminNameRuleResponse addRule(@Valid @RequestBody NameRuleRequest request) throws ApiException {
        var admin = getLoggedInUser();
        var rule = request.kind() == CatalogueNameRule.Kind.NOT_A_FOOD
                ? nameRules.notAFood(request.name(), admin)
                : nameRules.neverLinkTo(request.name(), catalogueService.getFood(request.catalogueFoodId()), admin);
        return AdminNameRuleResponse.fromEntity(rule);
    }

    @Operation(summary = "Delete a name rule", description = "Links made while it held stay as they are.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) throws ElementNotFound {
        nameRules.deleteRule(id);
    }
}
