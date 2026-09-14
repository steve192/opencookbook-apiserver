package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

import java.time.Clock;
import java.time.Duration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.nutrition.NutritionDatasetImport;
import com.sterul.opencookbookapiserver.repositories.NutritionDatasetImportRepository;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

import lombok.extern.slf4j.Slf4j;

/** Records dataset imports; each call commits separately so other instances see claims at once. */
@Component
@Slf4j
public class DatasetImportLedger {

    /** A RUNNING import older than this was interrupted and may be taken over. */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(30);

    private final NutritionDatasetImportRepository repository;
    private final Clock clock;

    public DatasetImportLedger(NutritionDatasetImportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** False if already imported or being imported elsewhere. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(NutritionDataset.Manifest manifest) {
        var now = clock.instant();
        var existing = repository.findById(manifest.checksum());
        if (existing.isEmpty()) {
            try {
                repository.saveAndFlush(NutritionDatasetImport.builder()
                        .checksum(manifest.checksum())
                        .label(manifest.label())
                        .status(NutritionDatasetImport.Status.RUNNING)
                        .startedAt(now)
                        .build());
                return true;
            } catch (DataIntegrityViolationException claimedConcurrently) {
                log.info("Nutrition dataset {} is being imported by another instance", manifest.label());
                return false;
            }
        }
        var entry = existing.get();
        var abandoned = entry.getStatus() == NutritionDatasetImport.Status.RUNNING
                && entry.getStartedAt().plus(ABANDONED_AFTER).isBefore(now);
        if (entry.getStatus() == NutritionDatasetImport.Status.FAILED || abandoned) {
            entry.setStatus(NutritionDatasetImport.Status.RUNNING);
            entry.setStartedAt(now);
            entry.setFinishedAt(null);
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(NutritionDataset.Manifest manifest, NutritionDatasetImport.Status status, String report) {
        var entry = repository.findById(manifest.checksum()).orElseThrow();
        entry.setStatus(status);
        entry.setFinishedAt(clock.instant());
        entry.setReport(report);
    }
}
