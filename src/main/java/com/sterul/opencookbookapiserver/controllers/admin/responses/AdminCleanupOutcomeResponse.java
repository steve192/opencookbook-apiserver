package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.services.ingredients.IngredientNameCleanupService;

public record AdminCleanupOutcomeResponse(int renamed, int merged, int linesChanged, int skipped) {

    public static AdminCleanupOutcomeResponse fromResult(IngredientNameCleanupService.Outcome outcome) {
        return new AdminCleanupOutcomeResponse(outcome.renamed(), outcome.merged(), outcome.linesChanged(),
                outcome.skipped());
    }
}
