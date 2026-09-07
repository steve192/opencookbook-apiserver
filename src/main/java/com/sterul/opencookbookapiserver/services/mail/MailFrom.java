package com.sterul.opencookbookapiserver.services.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;

/**
 * Who a mail is from.
 *
 * The configured address, carrying a display name, so that an inbox shows "CookPal" rather than
 * noreply@ something. The name is not translated - it is a name.
 */
@Component
@Slf4j
public class MailFrom {

    private static final String DISPLAY_NAME = "CookPal";

    private final OpencookbookConfiguration configuration;

    public MailFrom(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Said once at startup rather than once per mail, because the failure otherwise only shows up
     * the first time somebody signs up and then only in a stack trace.
     */
    @PostConstruct
    void warnWhenUnset() {
        var configured = configuration.getMailFrom();
        if (configured == null || configured.isBlank()) {
            log.warn("opencookbook.mailFrom is not set, so no mail can be sent - signing up, "
                    + "resetting a password and deleting an account will all fail to reach "
                    + "anybody. Set it to the address mails should come from.");
        }
    }

    public InternetAddress address() throws MessagingException {
        var configured = configuration.getMailFrom();
        try {
            return new InternetAddress(configured, DISPLAY_NAME, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is not going anywhere; an unsent mail would be a poor way to find out.
            throw new AddressException("Could not build the sender address from " + configured);
        }
    }
}
