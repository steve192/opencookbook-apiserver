package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.services.ml.MlJobService;

import lombok.extern.slf4j.Slf4j;

/** Keeps this side's view of a job in step with the subsystem's. */
@EnableScheduling
@Configuration
@ConditionalOnMlConfigured
@Slf4j
public class MlJobPollingCronjob {

    private final MlJobService mlJobService;

    public MlJobPollingCronjob(MlJobService mlJobService) {
        this.mlJobService = mlJobService;
    }

    @Scheduled(fixedDelayString = "PT${opencookbook.ml.poll-interval-seconds:2}S")
    public void refreshUnfinishedJobs() {
        mlJobService.refreshUnfinishedJobs();
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void deleteExpiredJobs() {
        var deleted = mlJobService.deleteExpiredJobs();
        if (deleted > 0) {
            log.info("Deleted {} expired machine learning job(s)", deleted);
        }
    }
}
