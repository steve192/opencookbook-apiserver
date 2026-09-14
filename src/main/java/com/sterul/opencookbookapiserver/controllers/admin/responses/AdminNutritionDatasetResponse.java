package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.nutrition.NutritionDatasetImport;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/** The shipped dataset and its imports into this instance. */
public record AdminNutritionDatasetResponse(
        String label,
        String checksum,
        int foods,
        List<NutritionDataset.Attribution> attributions,
        List<NutritionDataset.SourceRelease> sources,
        List<Import> imports) {

    public record Import(String checksum, String label, NutritionDatasetImport.Status status, Instant startedAt,
            Instant finishedAt, String report) {

        static Import fromEntity(NutritionDatasetImport entry) {
            return new Import(entry.getChecksum(), entry.getLabel(), entry.getStatus(), entry.getStartedAt(),
                    entry.getFinishedAt(), entry.getReport());
        }
    }

    public static AdminNutritionDatasetResponse of(NutritionDataset.Manifest manifest, List<NutritionDatasetImport> imports) {
        return new AdminNutritionDatasetResponse(manifest.label(), manifest.checksum(), manifest.foods(),
                manifest.attributions(), manifest.sources(), imports.stream().map(Import::fromEntity).toList());
    }
}
