package com.sterul.opencookbookapiserver.services.mail;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.AppLinkFactory;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether this instance can send mail. Mail is optional: it needs an SMTP host, a sender address,
 * and the configured instance address as well, because the links in a mail would otherwise be
 * built from the Host header of whoever asked for the mail.
 */
@Component
@Slf4j
public class MailAvailability {

    private final OpencookbookConfiguration configuration;
    private final AppLinkFactory appLinks;

    public MailAvailability(OpencookbookConfiguration configuration, AppLinkFactory appLinks) {
        this.configuration = configuration;
        this.appLinks = appLinks;
    }

    /** Said once at startup, as the failure otherwise only shows up when somebody needs a mail. */
    @PostConstruct
    void warnWhenSomethingIsMissing() {
        if (!hasSmtpHost() || isEnabled()) {
            return;
        }
        if (!hasSender()) {
            log.warn("opencookbook.smtpHost is set but opencookbook.mailFrom is not, so this instance "
                    + "sends no mail. Set mailFrom to the address mails should come from.");
        }
        if (appLinks.configuredInstanceUrl().isEmpty()) {
            log.warn("opencookbook.smtpHost is set but opencookbook.instanceURL is not, so this instance "
                    + "sends no mail: its links would be built from the Host header of whoever asks. "
                    + "Set instanceURL to the address people open the app on.");
        }
    }

    public boolean hasSmtpHost() {
        return isSet(configuration.getSmtpHost());
    }

    private boolean hasSender() {
        return isSet(configuration.getMailFrom());
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return hasSmtpHost() && hasSender() && appLinks.configuredInstanceUrl().isPresent();
    }

    public void requireEnabled() {
        if (!isEnabled()) {
            throw new ApiException(ApiErrorCode.MAIL_NOT_CONFIGURED);
        }
    }

    public void requireSmtpHost() {
        if (!hasSmtpHost()) {
            throw new ApiException(ApiErrorCode.MAIL_NOT_CONFIGURED);
        }
    }
}
