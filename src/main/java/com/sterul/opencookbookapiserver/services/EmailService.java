package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final OpencookbookConfiguration opencookbookConfiguration;

    public EmailService(JavaMailSender javaMailSender, OpencookbookConfiguration opencookbookConfiguration) {
        this.javaMailSender = javaMailSender;
        this.opencookbookConfiguration = opencookbookConfiguration;
    }

    public void sendActivationMail(ActivationLink activationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(opencookbookConfiguration.getMailFrom());
        message.setTo(activationLink.getUser().getEmailAddress());
        message.setSubject("CookPal - Activate your account");
        message.setText("You have just created an account. To activate your account click this link: "
                + opencookbookConfiguration.getInstanceURL() + "/api/v1/user/activate?activationId=" + activationLink.getId());

        javaMailSender.send(message);
    }

}
