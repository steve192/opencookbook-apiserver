package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.services.invitations.InvitationService;

import lombok.extern.slf4j.Slf4j;

/** Removes invitation links that have outlived their validity. */
@EnableScheduling
@Configuration
@Slf4j
public class InvitationDeletionJob {

    private final InvitationService invitationService;

    public InvitationDeletionJob(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteExpiredInvitations() {
        log.info("Deleting expired invitations");
        var deleted = invitationService.deleteExpiredInvitations();
        log.info("Deleted {} expired invitations", deleted);
    }
}
