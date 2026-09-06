package com.sterul.opencookbookapiserver.configurations;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailConfiguration {

    @Bean
    public JavaMailSender getJavaMailSender(OpencookbookConfiguration opencookbookConfiguration) {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(opencookbookConfiguration.getSmtpHost());
        mailSender.setPort(opencookbookConfiguration.getSmtpPort());

        mailSender.setUsername(opencookbookConfiguration.getSmtpUsername());
        mailSender.setPassword(opencookbookConfiguration.getSmtpPassword());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", opencookbookConfiguration.getSmtpProtocol());
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", opencookbookConfiguration.getSmtpStartTLS());
        // props.put("mail.debug", "true");

        return mailSender;
    }

    /**
     * The engine the mail templates are rendered by.
     *
     * The macro library is what makes three mails look like one product: every body template is
     * written in terms of {@code #heading}, {@code #paragraph} and {@code #button}, and what
     * those look like lives only in mailtemplates/macros.vm.
     */
    @Bean
    public VelocityEngine mailVelocityEngine() throws VelocityException {
        var engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        engine.setProperty("resource.loader.classpath.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        engine.setProperty(RuntimeConstants.INPUT_ENCODING, StandardCharsets.UTF_8.name());
        engine.setProperty(RuntimeConstants.VM_LIBRARY, "mailtemplates/macros.vm");
        engine.init();
        return engine;
    }

    /**
     * The mail texts, in every language there is a bundle for.
     *
     * Its own message source rather than the application-wide one, because these are the only
     * translated strings the server has: the app translates its own interface, and nothing here
     * should start looking like a second place to do that.
     */
    @Bean
    public MessageSource mailMessageSource() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/mail");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Without this, a key missing from a translation is answered in whatever language the
        // server happens to be configured in rather than in English.
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
