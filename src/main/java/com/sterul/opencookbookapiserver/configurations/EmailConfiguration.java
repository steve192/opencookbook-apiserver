package com.sterul.opencookbookapiserver.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class EmailConfiguration {
    @Bean
    public JavaMailSender getJavaMailSender(OpencookbookConfiguration opencookbookConfiguration) {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(opencookbookConfiguration.getSmtpHost());
        mailSender.setPort(opencookbookConfiguration.getSmtpPort());

        mailSender.setUsername(opencookbookConfiguration.getSmtpUsername());
        mailSender.setPassword(opencookbookConfiguration.getSmtpPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", opencookbookConfiguration.getSmtpProtocol());
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", opencookbookConfiguration.getSmtpStartTLS());
        props.put("mail.debug", "true");

        return mailSender;
    }
}

