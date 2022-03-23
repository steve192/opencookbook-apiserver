package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.StringWriter;

@Service
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final OpencookbookConfiguration opencookbookConfiguration;
    private final VelocityEngine velocityEngine;

    public EmailService(JavaMailSender javaMailSender, OpencookbookConfiguration opencookbookConfiguration, VelocityEngine velocityEngine) {
        this.javaMailSender = javaMailSender;
        this.opencookbookConfiguration = opencookbookConfiguration;
        this.velocityEngine = velocityEngine;
    }

    private void sendGeneralMail(String mailTitle, String mailText, String receiver) throws MessagingException {
        var message = javaMailSender.createMimeMessage();
        var messageHelper = new MimeMessageHelper(message, true);

        var template = velocityEngine.getTemplate("mailtemplates/generalMail.vm");
        var context = new VelocityContext();
        context.put("mailText", mailText);
        var stringWriter = new StringWriter();

        template.merge(context, stringWriter);

        messageHelper.setText(stringWriter.toString(), true);
        messageHelper.setFrom(opencookbookConfiguration.getMailFrom());
        messageHelper.setTo(receiver);
        messageHelper.setSubject(mailTitle);
        javaMailSender.send(message);
    }

    public void sendActivationMail(ActivationLink activationLink) throws MessagingException {
        var template = velocityEngine.getTemplate("mailtemplates/activationMailText.vm");
        var context = new VelocityContext();
        context.put("activationLink", opencookbookConfiguration.getInstanceURL() + "/activateAccount?activationId=" + activationLink.getId());
        var stringWriter = new StringWriter();

        template.merge(context, stringWriter);

        sendGeneralMail(
                "CookPal - Activate your account",
                stringWriter.toString(),
                activationLink.getUser().getEmailAddress()
        );
    }

    public void sendPasswordResetMail(PasswordResetLink link) throws MessagingException {
        var template = velocityEngine.getTemplate("mailtemplates/passwordResetMailText.vm");
        var context = new VelocityContext();
        context.put("link", opencookbookConfiguration.getInstanceURL() + "/resetPassword?id=" + link.getId());
        var stringWriter = new StringWriter();

        template.merge(context, stringWriter);

        sendGeneralMail(
                "CookPal - Reset password",
                stringWriter.toString(),
                link.getUser().getEmailAddress()
        );
    }
}
