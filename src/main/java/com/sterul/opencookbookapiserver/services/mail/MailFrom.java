package com.sterul.opencookbookapiserver.services.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Who a mail is from.
 *
 * The configured address, carrying a display name, so that an inbox shows "CookPal" rather than
 * noreply@ something. The name is not translated - it is a name.
 */
@Component
public class MailFrom {

    private static final String DISPLAY_NAME = "CookPal";

    private final OpencookbookConfiguration configuration;

    public MailFrom(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    public InternetAddress address() throws MessagingException {
        var configured = configuration.getMailFrom();
        if (configured == null || configured.isBlank()) {
            throw new AddressException("opencookbook.mailFrom is not set");
        }
        try {
            return new InternetAddress(configured, DISPLAY_NAME, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is not going anywhere; an unsent mail would be a poor way to find out.
            throw new AddressException("Could not build the sender address from " + configured);
        }
    }
}
