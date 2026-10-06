package com.sterul.opencookbookapiserver.cronjobs;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.services.retention.AccountRetentionService;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/** Warns and deletes accounts nobody uses any more, one account at a time. */
@EnableScheduling
@Configuration
@Slf4j
public class AccountRetentionJob {

    private final AccountRetentionService retention;
    private final Clock clock;

    public AccountRetentionJob(AccountRetentionService retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void enforceRetention() {
        var today = LocalDate.now(clock);
        var candidates = retention.candidates(today);
        for (var userId : candidates) {
            try {
                retention.advance(userId, today);
            } catch (MessagingException | RuntimeException e) {
                log.error("Data retention failed for user {}, trying again tomorrow", userId, e);
            }
        }
        log.info("Checked {} accounts for data retention", candidates.size());
    }
}
