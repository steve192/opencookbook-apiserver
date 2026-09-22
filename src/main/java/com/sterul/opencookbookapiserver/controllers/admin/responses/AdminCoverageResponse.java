package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.util.List;
import java.util.Map;

import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutrition;
import com.sterul.opencookbookapiserver.services.nutrition.reports.CoverageReport;

public record AdminCoverageResponse(int recipeCount, Map<RecipeNutrition.Status, Long> recipesByStatus,
        long lineCount, long warningLineCount, List<Count> warningCauses, List<Count> namesCausingWarnings) {

    public record Count(String key, long count) {
    }

    public static AdminCoverageResponse fromResult(CoverageReport.Coverage coverage) {
        return new AdminCoverageResponse(coverage.recipeCount(), coverage.recipesByStatus(), coverage.lineCount(),
                coverage.warningLineCount(), counts(coverage.warningCauses()),
                counts(coverage.namesCausingWarnings()));
    }

    private static List<Count> counts(List<CoverageReport.Count> counts) {
        return counts.stream().map(count -> new Count(count.key(), count.count())).toList();
    }
}
