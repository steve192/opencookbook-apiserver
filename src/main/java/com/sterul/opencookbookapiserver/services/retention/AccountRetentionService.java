package com.sterul.opencookbookapiserver.services.retention;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/** Warns and then deletes accounts nobody uses any more. Administrators and demo accounts are kept. */
@Service
@Slf4j
public class AccountRetentionService {

    private final UserRepository users;
    private final UserService userService;
    private final EmailService emailService;
    private final MailAvailability mail;
    private final OpencookbookConfiguration.Retention retention;
    private final InactivityCountdown countdown;
    private final Clock clock;

    public AccountRetentionService(UserRepository users, UserService userService, EmailService emailService,
            MailAvailability mail, OpencookbookConfiguration configuration, Clock clock) {
        this.users = users;
        this.userService = userService;
        this.emailService = emailService;
        this.mail = mail;
        this.retention = configuration.getRetention();
        this.countdown = new InactivityCountdown(retention.getInactiveAccountMonths());
        this.clock = clock;
    }

    /** The accounts that may have a step due today; none while retention is off. */
    public List<Long> candidates(LocalDate today) {
        if (!retention.isEnabled()) {
            return List.of();
        }
        return users.findIdsOfRolelessUsersInactiveSince(countdown.dueCandidatesActiveBefore(today));
    }

    /**
     * Takes the account's next step, if one is due. The account stays locked meanwhile, so a sign in either comes first
     * and stops the countdown, or finds the account gone. A notice that could not be mailed is not recorded.
     */
    @Transactional(rollbackFor = MessagingException.class)
    public void advance(Long userId, LocalDate today) throws MessagingException {
        var account = users.lockById(userId);
        if (account.isPresent()) {
            takeNextStep(account.get(), today);
        }
    }

    private void takeNextStep(CookpalUser account, LocalDate today) throws MessagingException {
        var step = countdown.next(account, today, mail.isEnabled());
        if (step.isEmpty()) {
            return;
        }
        switch (step.get()) {
            case InactivityCountdown.Notice notice -> {
                users.recordInactivityNotice(account.getUserId(), notice.number(), clock.instant());
                emailService.sendInactivityNotice(account, notice.deletionOn());
                log.info("Sent inactivity notice {} to user {}, deletion on {}", notice.number(), account,
                        notice.deletionOn());
            }
            case InactivityCountdown.Deletion deletion -> {
                log.info("Deleting user {}, unused since {}", account, account.lastActiveOrCreatedAt());
                userService.deleteInactiveUser(account);
            }
        }
    }
}
