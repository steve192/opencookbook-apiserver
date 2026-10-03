package com.sterul.opencookbookapiserver.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.services.exceptions.SignupDisabledException;
import com.sterul.opencookbookapiserver.services.instance.InstanceSettingsService;
import com.sterul.opencookbookapiserver.services.instance.SetupService;
import com.sterul.opencookbookapiserver.services.invitations.InvitationService;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/** Open and invited signups alike. */
@Service
@Transactional
@Slf4j
public class SignupService {

    private final SetupService setupService;
    private final InstanceSettingsService settings;
    private final InvitationService invitations;
    private final UserService userService;
    private final EmailService emailService;
    private final MailAvailability mail;

    public SignupService(SetupService setupService, InstanceSettingsService settings, InvitationService invitations,
            UserService userService, EmailService emailService, MailAvailability mail) {
        this.setupService = setupService;
        this.settings = settings;
        this.invitations = invitations;
        this.userService = userService;
        this.emailService = emailService;
        this.mail = mail;
    }

    /** @param invitation the token of an invitation link, or null for an open signup */
    public SignupState signup(String emailAddress, String password, String invitation) {
        setupService.requireSetUp();

        if (invitation != null) {
            // Redeemed first; an address already taken rolls the redemption back with the rest.
            invitations.redeem(invitation);
            userService.createUser(emailAddress, password, true, null);
            return SignupState.ACTIVE;
        }

        if (settings.getSignupMode() == SignupMode.INVITATION_ONLY) {
            throw new SignupDisabledException();
        }
        var user = userService.createUser(emailAddress, password, false, null);
        // Made even without mail, so the account can confirm itself once mail is enabled.
        var activationLink = userService.createActivationLink(user);
        if (!mail.isEnabled()) {
            return SignupState.AWAITING_APPROVAL;
        }
        sendActivationLink(activationLink);
        return SignupState.AWAITING_CONFIRMATION;
    }

    private void sendActivationLink(ActivationLink activationLink) {
        try {
            emailService.sendActivationMail(activationLink);
        } catch (MessagingException e) {
            // The account exists either way, and the link can be sent again from the login
            // screen, so a mail server that is down must not undo a signup.
            log.error("Error sending activation mail", e);
        }
    }
}
