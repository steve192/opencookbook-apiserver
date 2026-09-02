package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.services.sharing.ShareService;

import lombok.extern.slf4j.Slf4j;

/**
 * Removes share links that have outlived their validity.
 */
@EnableScheduling
@Configuration
@Slf4j
public class ShareDeletionJob {

    private final ShareService shareService;

    public ShareDeletionJob(ShareService shareService) {
        this.shareService = shareService;
    }

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteExpiredShares() {
        log.info("Deleting expired shares");
        var deleted = shareService.deleteExpiredShares();
        log.info("Deleted {} expired shares", deleted);
    }
}
