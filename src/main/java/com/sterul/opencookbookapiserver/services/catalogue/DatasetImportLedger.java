package com.sterul.opencookbookapiserver.services.catalogue;

import java.time.Clock;
import java.time.Duration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueDatasetImport;
import com.sterul.opencookbookapiserver.repositories.CatalogueDatasetImportRepository;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;

/** Records dataset imports; each call commits separately so other instances see claims at once. */
@Component
public class DatasetImportLedger {

    /** A RUNNING import older than this was interrupted and may be taken over. */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(30);

    private final CatalogueDatasetImportRepository repository;
    private final Clock clock;

    public DatasetImportLedger(CatalogueDatasetImportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * False if already imported or being imported elsewhere.
     *
     * @throws DataIntegrityViolationException if another instance claimed it at the same moment; the failed insert
     *                                         leaves this transaction rollback-only, so it cannot be caught in here
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(CatalogueDataset.Manifest manifest) {
        var now = clock.instant();
        var existing = repository.findById(manifest.checksum());
        if (existing.isEmpty()) {
            repository.saveAndFlush(CatalogueDatasetImport.builder()
                    .checksum(manifest.checksum())
                    .label(manifest.label())
                    .status(CatalogueDatasetImport.Status.RUNNING)
                    .startedAt(now)
                    .build());
            return true;
        }
        var entry = existing.get();
        var abandoned = entry.getStatus() == CatalogueDatasetImport.Status.RUNNING
                && entry.getStartedAt().plus(ABANDONED_AFTER).isBefore(now);
        if (entry.getStatus() == CatalogueDatasetImport.Status.FAILED || abandoned) {
            entry.setStatus(CatalogueDatasetImport.Status.RUNNING);
            entry.setStartedAt(now);
            entry.setFinishedAt(null);
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(CatalogueDataset.Manifest manifest, CatalogueDatasetImport.Status status, String report) {
        var entry = repository.findById(manifest.checksum()).orElseThrow();
        entry.setStatus(status);
        entry.setFinishedAt(clock.instant());
        entry.setReport(report);
    }
}
