package com.sterul.opencookbookapiserver.controllers.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.entities.ReviewedRun;
import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.ClassificationSource;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationProposal;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationRun;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.classification.RecipeClassificationService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/admin/recipes/classification-runs")
@Tag(name = "Recipe classification", description = "Deriving what recipes are, under review")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRecipeClassificationController extends BaseController {

    private final RecipeClassificationService classificationService;

    public AdminRecipeClassificationController(RecipeClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    public record PreviewRequest(@NotNull ClassificationKind kind,
            @NotNull RecipeClassificationRun.Scope scope) {
    }

    public record DecisionRequest(@NotEmpty List<Long> proposalIds,
            @NotNull RecipeClassificationProposal.Decision decision) {
    }

    public record RunResponse(Long id, ClassificationKind kind, RecipeClassificationRun.Scope scope,
            ReviewedRun.Status status, String basis, int proposalCount, int unreadableCount, int skippedCount,
            int appliedCount, String startedByEmailAddress, Instant appliedAt, Instant revertedAt, Instant createdOn) {

        static RunResponse fromEntity(RecipeClassificationRun run) {
            return new RunResponse(run.getId(), run.getKind(), run.getScope(), run.getStatus(), run.getBasis(),
                    run.getProposalCount(), run.getUnreadableCount(), run.getSkippedCount(), run.getAppliedCount(),
                    run.getStartedBy() == null ? null : run.getStartedBy().getEmailAddress(),
                    run.getAppliedAt(), run.getRevertedAt(), run.getCreatedOn());
        }
    }

    /**
     * @param proposedValue as the run's kind encodes it; for a diet, VEGAN, VEGETARIAN or MEAT
     * @param reason        why the run reached this, in the words a reviewer can check it by
     */
    public record ProposalResponse(Long id, Long recipeId, String recipeTitle, String proposedValue,
            String previousValue, ClassificationSource previousSource,
            RecipeClassificationProposal.Decision decision, String reason) {

        static ProposalResponse fromEntity(RecipeClassificationProposal proposal) {
            return new ProposalResponse(proposal.getId(), proposal.getRecipe().getId(), proposal.getRecipe().getTitle(),
                    proposal.getProposedValue(), proposal.getPreviousValue(), proposal.previousSource(),
                    proposal.getDecision(), proposal.getReason());
        }
    }

    @Operation(summary = "Every classification run, the latest first")
    @GetMapping
    public List<RunResponse> getRuns() {
        return classificationService.getRuns().stream().map(RunResponse::fromEntity).toList();
    }

    @Operation(summary = "Preview a classification run",
            description = "Reads every recipe in scope and records what it would make of it; changes no recipe. "
                    + "A value a person set is never in scope, and a recipe that cannot be read is recorded as "
                    + "skipped with a reason rather than guessed at. A kind this instance cannot classify is not "
                    + "found; diets need nutrition estimation.")
    @PostMapping
    public RunResponse preview(@Valid @RequestBody PreviewRequest request) throws ElementNotFound {
        return RunResponse.fromEntity(
                classificationService.preview(request.kind(), request.scope(), getLoggedInUser()));
    }

    @Operation(summary = "One classification run")
    @GetMapping("/{id}")
    public RunResponse getRun(@PathVariable Long id) throws ElementNotFound {
        return RunResponse.fromEntity(classificationService.getRun(id));
    }

    @Operation(summary = "The proposals of a classification run")
    @GetMapping("/{id}/proposals")
    public List<ProposalResponse> getProposals(@PathVariable Long id) throws ElementNotFound {
        return classificationService.getProposals(id).stream().map(ProposalResponse::fromEntity).toList();
    }

    @Operation(summary = "Decide proposals of a previewed run")
    @PostMapping("/{id}/decisions")
    public List<ProposalResponse> decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request)
            throws ApiException {
        return classificationService.decide(id, request.proposalIds(), request.decision()).stream()
                .map(ProposalResponse::fromEntity).toList();
    }

    @Operation(summary = "Apply the accepted proposals",
            description = "Recipes whose value changed since the preview are skipped.")
    @PostMapping("/{id}/apply")
    public RunResponse apply(@PathVariable Long id) throws ApiException {
        return RunResponse.fromEntity(classificationService.apply(id));
    }

    @Operation(summary = "Revert an applied run",
            description = "Only recipes this run was the last to classify are restored.")
    @PostMapping("/{id}/revert")
    public RunResponse revert(@PathVariable Long id) throws ApiException {
        return RunResponse.fromEntity(classificationService.revert(id));
    }

    @Operation(summary = "Discard a previewed run")
    @PostMapping("/{id}/discard")
    public RunResponse discard(@PathVariable Long id) throws ApiException {
        return RunResponse.fromEntity(classificationService.discard(id));
    }
}
