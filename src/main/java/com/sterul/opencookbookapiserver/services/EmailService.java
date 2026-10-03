package com.sterul.opencookbookapiserver.services;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.MessagingException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.services.mail.MailFrom;
import com.sterul.opencookbookapiserver.services.mail.MailKind;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.mail.MailRenderer;

import lombok.extern.slf4j.Slf4j;

/**
 * Every mail this instance sends.
 *
 * The methods here decide what to say and to whom; how it looks is
 * {@link MailRenderer} and the templates, what language it is in is {@link MailLanguages}. Each
 * mail leaves as an html part and a plain text part, with the logo travelling inside it.
 */
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final MailFrom mailFrom;
    private final AccountLinkFactory accountLinks;
    private final MailRenderer mailRenderer;
    private final MailLanguages mailLanguages;

    public EmailService(JavaMailSender javaMailSender, MailFrom mailFrom, AccountLinkFactory accountLinks,
            MailRenderer mailRenderer, MailLanguages mailLanguages) {
        this.javaMailSender = javaMailSender;
        this.mailFrom = mailFrom;
        this.accountLinks = accountLinks;
        this.mailRenderer = mailRenderer;
        this.mailLanguages = mailLanguages;
    }

    public void sendActivationMail(ActivationLink activationLink) throws MessagingException {
        send(MailKind.ACTIVATION, activationLink.getUser(), Map.of(
                "activationLink", accountLinks.activation(activationLink)));
    }

    public void sendPasswordResetMail(PasswordResetLink link) throws MessagingException {
        send(MailKind.PASSWORD_RESET, link.getUser(), Map.of(
                "resetLink", accountLinks.passwordReset(link)));
    }

    public void sendAccountDeletedMail(String emailAddress, Locale language) throws MessagingException {
        send(MailKind.ACCOUNT_DELETED, emailAddress, language, Map.of());
    }

    /** Nobody owns the address yet, so it is written in the inviting administrator's language. */
    public void sendInvitationMail(Invitation invitation, int validForDays, String receiver)
            throws MessagingException {
        send(MailKind.INVITATION, receiver, mailLanguages.forUser(invitation.getCreatedBy()), Map.of(
                "invitationLink", accountLinks.invitation(invitation),
                "validForDays", validForDays));
    }

    public void sendTestMail(CookpalUser administrator) throws MessagingException {
        send(MailKind.TEST, administrator, Map.of());
    }

    private void send(MailKind kind, CookpalUser user, Map<String, Object> model) throws MessagingException {
        send(kind, user.getEmailAddress(), mailLanguages.forUser(user), model);
    }

    private void send(MailKind kind, String receiver, Locale language, Map<String, Object> model)
            throws MessagingException {
        log.info("Sending {} mail in {} to {}", kind, language.getLanguage(), receiver);

        var mail = mailRenderer.render(kind, language, receiver, model);

        var message = javaMailSender.createMimeMessage();
        // Related rather than merely alternative: the two bodies are alternatives to each other,
        // and the logo belongs to the html one rather than hanging off the mail as an attachment.
        var messageHelper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());

        messageHelper.setFrom(mailFrom.address());
        messageHelper.setTo(receiver);
        messageHelper.setSubject(mail.subject());
        // Plain text first: this overload reads them in the order a client should prefer them
        // last, and puts the html part second in the message for exactly that reason.
        messageHelper.setText(mail.text(), mail.html());
        messageHelper.addInline(MailRenderer.LOGO_CONTENT_ID, new ClassPathResource(MailRenderer.LOGO_RESOURCE),
                "image/png");

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            // Spring reports an unreachable server unchecked; every caller is written against the
            // declared failure, and signing up or deleting an account must survive it.
            throw new MessagingException("Sending the " + kind + " mail failed", e);
        }
    }

}
