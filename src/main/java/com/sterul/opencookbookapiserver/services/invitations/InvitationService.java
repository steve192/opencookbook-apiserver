package com.sterul.opencookbookapiserver.services.invitations;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.InvitationRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.FailureReason;
import com.sterul.opencookbookapiserver.services.SecretTokens;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/** Invitation links: the only way an administrator brings somebody in. Single use, expiring, never tied to an address. */
@Service
@Transactional
@Slf4j
public class InvitationService {

    public static final Set<Integer> VALIDITY_DAYS = Set.of(1, 7, 30);
    public static final int DEFAULT_VALIDITY_DAYS = 7;

    private final InvitationRepository repository;
    private final EmailService emailService;
    private final MailAvailability mail;
    private final Clock clock;

    public InvitationService(InvitationRepository repository, EmailService emailService, MailAvailability mail,
            Clock clock) {
        this.repository = repository;
        this.emailService = emailService;
        this.mail = mail;
        this.clock = clock;
    }

    /**
     * @param sendTo where to mail the link, or null to only hand it over; a failed mail takes the invitation back
     */
    public Invitation create(CookpalUser creator, int validForDays, String sendTo) {
        if (sendTo != null) {
            mail.requireEnabled();
        }
        var invitation = repository.save(Invitation.builder()
                .id(SecretTokens.generate())
                .createdBy(creator)
                .expiresAt(clock.instant().plus(Duration.ofDays(validForDays)))
                .build());
        log.info("Administrator {} created an invitation valid for {} days", creator.getUserId(), validForDays);
        if (sendTo != null) {
            mailTo(invitation, validForDays, sendTo);
        }
        return invitation;
    }

    private void mailTo(Invitation invitation, int validForDays, String sendTo) {
        try {
            emailService.sendInvitationMail(invitation, validForDays, sendTo);
        } catch (MessagingException e) {
            throw ApiException.withReason(ApiErrorCode.MAIL_DELIVERY_FAILED, FailureReason.of(e), e);
        }
    }

    public List<Invitation> getOpenInvitations() {
        return repository.findAllByExpiresAtAfterOrderByCreatedOnDesc(clock.instant());
    }

    public void revoke(String id) {
        log.info("Revoking an invitation");
        repository.delete(repository.findById(id).orElseThrow(ElementNotFound::new));
    }

    /** Uses the invitation up. One answer for expired, revoked, used and never-existed alike. */
    public void redeem(String token) {
        var invitation = repository.findLockedById(token)
                .filter(candidate -> !candidate.hasExpired(clock.instant()))
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVITATION_INVALID, "Invitation is not valid"));
        repository.delete(invitation);
    }

    public int deleteExpiredInvitations() {
        return repository.deleteByExpiresAtBefore(clock.instant());
    }
}
