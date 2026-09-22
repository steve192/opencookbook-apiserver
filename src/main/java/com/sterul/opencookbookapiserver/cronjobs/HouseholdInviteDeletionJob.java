package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.households.ConditionalOnHouseholdsEnabled;
import com.sterul.opencookbookapiserver.services.households.HouseholdInviteService;

import lombok.extern.slf4j.Slf4j;

/** Removes invite links that have outlived their validity. */
@EnableScheduling
@Configuration
@ConditionalOnHouseholdsEnabled
@Slf4j
public class HouseholdInviteDeletionJob {

    private final HouseholdInviteService inviteService;

    public HouseholdInviteDeletionJob(HouseholdInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteExpiredInvites() {
        log.info("Deleting expired household invites");
        var deleted = inviteService.deleteExpiredInvites();
        log.info("Deleted {} expired household invites", deleted);
    }
}
