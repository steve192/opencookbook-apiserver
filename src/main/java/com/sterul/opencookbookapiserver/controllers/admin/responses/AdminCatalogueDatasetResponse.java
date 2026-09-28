package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueDatasetImport;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;

/** The shipped dataset and its imports into this instance. */
public record AdminCatalogueDatasetResponse(
        String label,
        String checksum,
        int foods,
        List<CatalogueDataset.Attribution> attributions,
        List<CatalogueDataset.SourceRelease> sources,
        List<Import> imports) {

    public record Import(String checksum, String label, CatalogueDatasetImport.Status status, Instant startedAt,
            Instant finishedAt, String report) {

        static Import fromEntity(CatalogueDatasetImport entry) {
            return new Import(entry.getChecksum(), entry.getLabel(), entry.getStatus(), entry.getStartedAt(),
                    entry.getFinishedAt(), entry.getReport());
        }
    }

    public static AdminCatalogueDatasetResponse of(CatalogueDataset.Manifest manifest, List<CatalogueDatasetImport> imports) {
        return new AdminCatalogueDatasetResponse(manifest.label(), manifest.checksum(), manifest.foods(),
                manifest.attributions(), manifest.sources(), imports.stream().map(Import::fromEntity).toList());
    }
}
