package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.services.nutrition.reports.IngredientNameReport;

/** @param useCount recipe lines using the name */
public record AdminUnmatchedNameResponse(String name, String language, long userCount, long useCount, List<Long> ingredientIds,
        List<Candidate> candidates) {

    public record Candidate(AdminFoodReference food, double confidence) {
    }

    public static AdminUnmatchedNameResponse of(IngredientNameReport.UnmatchedName name) {
        return new AdminUnmatchedNameResponse(name.name(), name.language(), name.userCount(), name.useCount(), name.ingredientIds(),
                name.candidates().stream()
                        .map(candidate -> new Candidate(AdminFoodReference.of(candidate.food()), candidate.confidence()))
                        .toList());
    }
}
