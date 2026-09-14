package com.sterul.opencookbookapiserver.controllers.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientNameCleanupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Moves amounts and units out of ingredient names. */
@RestController
@RequestMapping("/api/v1/admin/ingredients/name-cleanup")
@Tag(name = "Ingredients", description = "Admin ingredient api")
public class AdminIngredientCleanupController {

    private final IngredientNameCleanupService cleanupService;

    public AdminIngredientCleanupController(IngredientNameCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    public record ProposalResponse(Long ingredientId, String ownerEmailAddress, Instant lastChange, String name,
            String proposedName, Float amount, String unit, Long mergesIntoIngredientId,
            List<IngredientNameCleanupService.LineChange> lines) {

        static ProposalResponse of(IngredientNameCleanupService.Proposal proposal) {
            return new ProposalResponse(proposal.ingredientId(), proposal.ownerEmailAddress(), proposal.lastChange(), proposal.name(),
                    proposal.split().name(), proposal.split().amount(), proposal.split().unit(), proposal.mergesIntoIngredientId(),
                    proposal.lines());
        }
    }

    /** @param lastChange as previewed */
    public record DecisionRequest(@NotNull Long ingredientId, @NotBlank String name, @NotNull Instant lastChange) {
    }

    public record ApplyRequest(@NotEmpty List<@Valid DecisionRequest> decisions) {
    }

    @Operation(summary = "Preview: ingredient names holding an amount or unit, and what cleaning them would change")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<ProposalResponse> preview() {
        return cleanupService.preview().stream().map(ProposalResponse::of).toList();
    }

    @Operation(summary = "Clean the names of these ingredients",
            description = "Moves amount and unit into the recipe lines, renames or merges the ingredient. Not revertible. "
                    + "Ingredients changed since the preview are skipped.")
    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('ADMIN')")
    public IngredientNameCleanupService.Outcome apply(@Valid @RequestBody ApplyRequest request) throws ApiException {
        return cleanupService.apply(request.decisions().stream()
                .map(decision -> new IngredientNameCleanupService.Decision(decision.ingredientId(), decision.name(),
                        decision.lastChange()))
                .toList());
    }
}
