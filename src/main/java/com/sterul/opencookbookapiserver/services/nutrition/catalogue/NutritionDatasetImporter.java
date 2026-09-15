package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.nutrition.NutritionDatasetImport;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

import lombok.extern.slf4j.Slf4j;

/** Imports the shipped dataset once per release, in the background; skipped if the checksum does not match. */
@Component
@ConditionalOnNutritionEnabled
@Slf4j
public class NutritionDatasetImporter {

    private final NutritionDatasetReader reader;
    private final DatasetImportLedger ledger;
    private final CatalogueDatasetApplier applier;
    private final TaskExecutor taskExecutor;
    private final ApplicationEventPublisher events;

    public NutritionDatasetImporter(NutritionDatasetReader reader, DatasetImportLedger ledger,
            CatalogueDatasetApplier applier, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            ApplicationEventPublisher events) {
        this.reader = reader;
        this.ledger = ledger;
        this.applier = applier;
        this.taskExecutor = taskExecutor;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importInBackground() {
        taskExecutor.execute(this::importShippedDataset);
    }

    public void importShippedDataset() {
        var manifest = reader.manifest();
        var computed = reader.computeChecksum();
        if (!computed.equals(manifest.checksum())) {
            log.error("Nutrition dataset {} is not imported: its files have checksum {}, its manifest says {}",
                    manifest.label(), computed, manifest.checksum());
            return;
        }
        if (!claim(manifest)) {
            return;
        }
        log.info("Importing nutrition dataset {} ({} foods)", manifest.label(), manifest.foods());
        try {
            var report = applier.apply(reader.catalogue());
            ledger.finish(manifest, NutritionDatasetImport.Status.DONE, String.join("\n", report));
            log.info("Nutrition dataset {} imported", manifest.label());
            events.publishEvent(new CatalogueChangedEvent("dataset " + manifest.label() + " imported"));
        } catch (RuntimeException failure) {
            log.error("Importing nutrition dataset {} failed", manifest.label(), failure);
            ledger.finish(manifest, NutritionDatasetImport.Status.FAILED, failure.toString());
        }
    }

    private boolean claim(NutritionDataset.Manifest manifest) {
        try {
            return ledger.claim(manifest);
        } catch (DataIntegrityViolationException claimedConcurrently) {
            log.info("Nutrition dataset {} is being imported by another instance", manifest.label());
            return false;
        }
    }
}
