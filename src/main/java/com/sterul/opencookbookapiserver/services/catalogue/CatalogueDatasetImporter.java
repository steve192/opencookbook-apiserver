package com.sterul.opencookbookapiserver.services.catalogue;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueDatasetImport;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

import lombok.extern.slf4j.Slf4j;

/** Imports the shipped dataset once per release; skipped if the checksum does not match. */
@Component
@Slf4j
public class CatalogueDatasetImporter {

    private final CatalogueDatasetReader reader;
    private final DatasetImportLedger ledger;
    private final CatalogueDatasetApplier applier;
    private final ApplicationEventPublisher events;

    public CatalogueDatasetImporter(CatalogueDatasetReader reader, DatasetImportLedger ledger,
            CatalogueDatasetApplier applier, ApplicationEventPublisher events) {
        this.reader = reader;
        this.ledger = ledger;
        this.applier = applier;
        this.events = events;
    }

    public void importShippedDataset() {
        var manifest = reader.manifest();
        var computed = reader.computeChecksum();
        if (!computed.equals(manifest.checksum())) {
            log.error("Catalogue dataset {} is not imported: its files have checksum {}, its manifest says {}",
                    manifest.label(), computed, manifest.checksum());
            return;
        }
        if (!claim(manifest)) {
            return;
        }
        log.info("Importing catalogue dataset {} ({} foods)", manifest.label(), manifest.foods());
        try {
            var report = applier.apply(reader.catalogue());
            ledger.finish(manifest, CatalogueDatasetImport.Status.DONE, String.join("\n", report));
            log.info("Catalogue dataset {} imported", manifest.label());
            events.publishEvent(new CatalogueChangedEvent("dataset " + manifest.label() + " imported"));
        } catch (RuntimeException failure) {
            log.error("Importing catalogue dataset {} failed", manifest.label(), failure);
            ledger.finish(manifest, CatalogueDatasetImport.Status.FAILED, failure.toString());
        }
    }

    private boolean claim(CatalogueDataset.Manifest manifest) {
        try {
            return ledger.claim(manifest);
        } catch (DataIntegrityViolationException claimedConcurrently) {
            log.info("Catalogue dataset {} is being imported by another instance", manifest.label());
            return false;
        }
    }
}
