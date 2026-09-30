package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.services.SignInService;

import lombok.extern.slf4j.Slf4j;

/** Removes refresh tokens that can no longer renew a sign in. */
@EnableScheduling
@Configuration
@Slf4j
public class RefreshTokenDeletionJob {

    private final SignInService signInService;

    public RefreshTokenDeletionJob(SignInService signInService) {
        this.signInService = signInService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void deleteStaleRefreshTokens() {
        var deleted = signInService.deleteStale();
        log.info("Deleted {} stale refresh tokens", deleted);
    }
}
