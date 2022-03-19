package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
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

    public void sendActivationMail(ActivationLink activationLink) throws MessagingException {
        var message = javaMailSender.createMimeMessage();
        var messageHelper = new MimeMessageHelper(message,true);

        var template = velocityEngine.getTemplate("./mailtemplates/activation.vm");
        var context = new VelocityContext();
        context.put("activationLink",opencookbookConfiguration.getInstanceURL() + "/api/v1/users/activate?activationId=" + activationLink.getId() );
        var stringWriter = new StringWriter();

        template.merge(context, stringWriter);

        messageHelper.setText(stringWriter.toString(),true);
        messageHelper.setFrom(opencookbookConfiguration.getMailFrom());
        messageHelper.setTo(activationLink.getUser().getEmailAddress());
        messageHelper.setSubject("CookPal - Activate your account");
        javaMailSender.send(message);
    }

}
