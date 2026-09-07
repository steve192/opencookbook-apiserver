package com.sterul.opencookbookapiserver.unit.services.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.sterul.opencookbookapiserver.configurations.EmailConfiguration;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.services.AppLinkFactory;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.mail.MailFrom;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.mail.MailMessages;
import com.sterul.opencookbookapiserver.services.mail.MailRenderer;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * The message that actually leaves the building.
 *
 * The templates are checked next door; what is checked here is everything between a rendered
 * mail and a well formed one, because those are the faults a preview cannot show: a subject that
 * arrives as mojibake, a mail with no plain text alternative, a logo that was referenced but
 * never attached.
 */
class EmailServiceTest {

    private final OpencookbookConfiguration configuration = new OpencookbookConfiguration();
    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private EmailService cut;

    @BeforeEach
    void setUp() {
        configuration.setInstanceURL("https://cookpal.example/");
        configuration.setMailFrom("hello@cookpal.example");

        var emailConfiguration = new EmailConfiguration();
        cut = new EmailService(
                javaMailSender,
                new MailFrom(configuration),
                new AppLinkFactory(configuration),
                new MailRenderer(emailConfiguration.mailVelocityEngine(),
                        new MailMessages(emailConfiguration.mailMessageSource()), configuration),
                new MailLanguages());

        // A real session, so that the message is encoded exactly as it would be on the way out.
        when(javaMailSender.createMimeMessage()).thenAnswer(call -> new JavaMailSenderImpl().createMimeMessage());
    }

    @Test
    void anActivationMailIsAddressedAndSigned() throws Exception {
        cut.sendActivationMail(activationLinkFor(user("someone@cookpal.invalid", "en")));

        var sent = captureSentMessage();
        assertEquals("Confirm your CookPal account", sent.getSubject());
        assertEquals("someone@cookpal.invalid", sent.getAllRecipients()[0].toString());
        assertEquals("CookPal <hello@cookpal.example>", sent.getFrom()[0].toString());
    }

    @Test
    void aGermanAccountIsWrittenToInGerman() throws Exception {
        cut.sendActivationMail(activationLinkFor(user("jemand@cookpal.invalid", "de")));

        // Read back through the api rather than off the wire, so that this passes only if the
        // header was encoded in a way a client can decode again.
        assertEquals("Bestätige deinen CookPal Account", captureSentMessage().getSubject());
    }

    @Test
    void everyMailCarriesBothAPlainTextAndAnHtmlPartAndTheLogo() throws Exception {
        cut.sendPasswordResetMail(passwordResetLinkFor(user("someone@cookpal.invalid", "en")));

        var raw = rawMessage(captureSentMessage());
        assertTrue(raw.contains("multipart/alternative"), "there is no plain text alternative");
        assertTrue(raw.contains("multipart/related"), "the logo is not related to the html part");
        assertTrue(raw.contains("text/plain"), "there is no plain text part");
        assertTrue(raw.contains("text/html"), "there is no html part");
        assertTrue(raw.contains("Content-ID: <cookpal-logo>"), "the logo was referenced but not attached");
    }

    @Test
    void aTrailingSlashOnTheInstanceAddressDoesNotDoubleUpInTheLink() throws Exception {
        cut.sendPasswordResetMail(passwordResetLinkFor(user("someone@cookpal.invalid", "en")));

        var raw = rawMessage(captureSentMessage());
        assertTrue(raw.contains("https://cookpal.example/resetPassword?id=reset-id"), "the link is wrong");
        assertTrue(!raw.contains("cookpal.example//"), "the link has a doubled slash");
    }

    @Test
    void anAccountDeletedMailNeedsNoLinkAtAll() throws Exception {
        cut.sendAccountDeletedMail(user("someone@cookpal.invalid", "de"));

        assertEquals("Dein CookPal Account wurde gelöscht", captureSentMessage().getSubject());
    }

    private MimeMessage captureSentMessage() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    private String rawMessage(MimeMessage message) throws Exception {
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString();
    }

    private CookpalUser user(String emailAddress, String language) {
        var user = new CookpalUser();
        user.setEmailAddress(emailAddress);
        user.setLanguage(language);
        return user;
    }

    private ActivationLink activationLinkFor(CookpalUser user) throws MessagingException {
        var link = new ActivationLink();
        link.setId("activation-id");
        link.setUser(user);
        return link;
    }

    private PasswordResetLink passwordResetLinkFor(CookpalUser user) {
        var link = new PasswordResetLink();
        link.setId("reset-id");
        link.setUser(user);
        return link;
    }

    @Test
    void aMailLandsInTheDefaultLanguageWhenTheAccountNeverSaidWhichOne() throws Exception {
        cut.sendActivationMail(activationLinkFor(user("someone@cookpal.invalid", null)));

        assertEquals("Confirm your CookPal account", captureSentMessage().getSubject());
        assertEquals(Locale.ENGLISH, MailLanguages.DEFAULT);
    }
}
