package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkRun;

/** @param skippedCount ingredients changed after the preview, left alone by apply */
public record AdminRelinkRunResponse(
        Long id,
        IngredientRelinkRun.Scope scope,
        Float belowConfidence,
        int matcherVersion,
        String datasetLabel,
        IngredientRelinkRun.Status status,
        int proposalCount,
        int ingredientCount,
        int unchangedCount,
        int appliedCount,
        int skippedCount,
        String startedByEmailAddress,
        Instant createdOn,
        Instant appliedAt,
        Instant revertedAt) {

    public static AdminRelinkRunResponse fromEntity(IngredientRelinkRun run) {
        var startedBy = run.getStartedBy();
        return new AdminRelinkRunResponse(run.getId(), run.getScope(), run.getBelowConfidence(),
                run.getMatcherVersion(), run.getDatasetLabel(), run.getStatus(), run.getProposalCount(), run.getIngredientCount(),
                run.getUnchangedCount(), run.getAppliedCount(), run.getSkippedCount(),
                startedBy == null ? null : startedBy.getEmailAddress(), run.getCreatedOn(), run.getAppliedAt(), run.getRevertedAt());
    }
}
