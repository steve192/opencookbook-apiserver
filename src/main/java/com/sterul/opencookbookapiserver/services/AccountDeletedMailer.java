package com.sterul.opencookbookapiserver.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the mail only once the deletion is committed, so that a slow mail server neither holds
 * the transaction open nor announces a deletion that was rolled back.
 */
@Component
@Slf4j
public class AccountDeletedMailer {

    private final EmailService emailService;
    private final MailAvailability mail;

    public AccountDeletedMailer(EmailService emailService, MailAvailability mail) {
        this.emailService = emailService;
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void tellOwner(AccountDeletedEvent deleted) {
        if (!mail.isEnabled()) {
            return;
        }
        try {
            emailService.sendAccountDeletedMail(deleted.emailAddress(), deleted.language());
        } catch (MessagingException e) {
            log.error("Error sending account deletion mail to {}, ignoring", deleted.emailAddress(), e);
        }
    }
}
