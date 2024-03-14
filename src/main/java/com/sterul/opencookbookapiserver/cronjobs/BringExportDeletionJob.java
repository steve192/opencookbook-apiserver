package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.services.BringExportService;

import lombok.extern.slf4j.Slf4j;

@EnableScheduling
@Configuration
@Slf4j
public class BringExportDeletionJob {

    private BringExportService bringExportService;

    public BringExportDeletionJob(BringExportService bringExportService) {
        this.bringExportService = bringExportService;
    }

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteBringExports() {
        log.info("Deleting expired bring exports");
        bringExportService.deleteExpiredExports();
    }
    
}
