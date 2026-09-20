package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminRecipeResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminRelinkProposalResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminRelinkRunResponse;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkProposal;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkRun;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.nutrition.relinking.RelinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/admin/nutrition/relink-runs")
@Tag(name = "Nutrition relinking", description = "Relink runs: preview, decide, apply, revert")
@ConditionalOnNutritionEnabled
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRelinkController extends BaseController {

    private final RelinkService relinkService;

    public AdminRelinkController(RelinkService relinkService) {
        this.relinkService = relinkService;
    }

    /** @param belowConfidence for AUTOMATIC_BELOW_CONFIDENCE only */
    public record PreviewRequest(@NotNull IngredientRelinkRun.Scope scope, @DecimalMin("0") @DecimalMax("1") Float belowConfidence) {
    }

    /** @param remember for rejections: never propose this food for this name again */
    public record DecisionRequest(@NotEmpty List<Long> proposalIds, @NotNull IngredientRelinkProposal.Decision decision,
            boolean remember) {
    }

    @Operation(summary = "Every relink run, the latest first")
    @GetMapping
    public List<AdminRelinkRunResponse> getRuns() {
        return relinkService.getRuns().stream().map(AdminRelinkRunResponse::fromEntity).toList();
    }

    @Operation(summary = "Preview a relink run", description = "Proposes links for every name in scope; changes no ingredient.")
    @PostMapping
    public AdminRelinkRunResponse preview(@Valid @RequestBody PreviewRequest request) throws ApiException {
        return AdminRelinkRunResponse.fromEntity(relinkService.preview(
                new RelinkService.Scope(request.scope(), request.belowConfidence()), getLoggedInUser()));
    }

    @Operation(summary = "One relink run")
    @GetMapping("/{id}")
    public AdminRelinkRunResponse getRun(@PathVariable Long id) throws ElementNotFound {
        return AdminRelinkRunResponse.fromEntity(relinkService.getRun(id));
    }

    @Operation(summary = "The proposals of a relink run")
    @GetMapping("/{id}/proposals")
    public List<AdminRelinkProposalResponse> getProposals(@PathVariable Long id) throws ElementNotFound {
        return relinkService.getProposals(id).stream().map(AdminRelinkProposalResponse::fromEntity).toList();
    }

    @Operation(summary = "Decide proposals of a previewed run", description = "A remembered rejection becomes a name rule.")
    @PostMapping("/{id}/decisions")
    public List<AdminRelinkProposalResponse> decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request)
            throws ApiException {
        return relinkService.decide(id, request.proposalIds(), request.decision(), request.remember(), getLoggedInUser()).stream()
                .map(AdminRelinkProposalResponse::fromEntity).toList();
    }

    @Operation(summary = "Some recipes a proposal affects")
    @GetMapping("/{id}/proposals/{proposalId}/recipes")
    public List<AdminRecipeResponse> getSampleRecipes(@PathVariable Long id, @PathVariable Long proposalId) throws ElementNotFound {
        return relinkService.sampleRecipes(id, proposalId).stream().map(AdminRecipeResponse::fromEntity).toList();
    }

    @Operation(summary = "Apply the accepted proposals", description = "Ingredients changed since the preview are skipped.")
    @PostMapping("/{id}/apply")
    public AdminRelinkRunResponse apply(@PathVariable Long id) throws ApiException {
        return AdminRelinkRunResponse.fromEntity(relinkService.apply(id));
    }

    @Operation(summary = "Revert an applied run", description = "Only ingredients the run was the last to link are restored.")
    @PostMapping("/{id}/revert")
    public AdminRelinkRunResponse revert(@PathVariable Long id) throws ApiException {
        return AdminRelinkRunResponse.fromEntity(relinkService.revert(id));
    }

    @Operation(summary = "Discard a previewed run")
    @PostMapping("/{id}/discard")
    public AdminRelinkRunResponse discard(@PathVariable Long id) throws ApiException {
        return AdminRelinkRunResponse.fromEntity(relinkService.discard(id));
    }
}
