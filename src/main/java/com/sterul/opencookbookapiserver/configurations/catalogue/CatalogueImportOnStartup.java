package com.sterul.opencookbookapiserver.configurations.catalogue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.CatalogueDatasetImporter;

/** Imports a newly shipped catalogue once the server is up, without holding up the start. */
@Component
@ConditionalOnProperty(prefix = "opencookbook.catalogue", name = "import-on-startup", matchIfMissing = true)
public class CatalogueImportOnStartup {

    private final CatalogueDatasetImporter importer;
    private final TaskExecutor taskExecutor;

    public CatalogueImportOnStartup(CatalogueDatasetImporter importer,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.importer = importer;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importInBackground() {
        taskExecutor.execute(importer::importShippedDataset);
    }
}
