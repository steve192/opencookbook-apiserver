package com.sterul.opencookbookapiserver.services.mail;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * The translated texts, in a shape a template can ask questions of.
 *
 * Templates hold no prose at all - they call {@code $i18n.get("mail.activation.heading")} - so a
 * wording change never touches markup and a new translation never touches either.
 */
@Component
public class MailMessages {

    private final MessageSource messageSource;

    public MailMessages(@Qualifier("mailMessageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Translator forLocale(Locale locale) {
        return new Translator(messageSource, locale);
    }

    /**
     * The bundle bound to one language, handed to Velocity as {@code $i18n}.
     *
     * The two overloads are spelled out rather than left to varargs because Velocity picks a
     * method by counting arguments, and an ambiguous pick fails at render time rather than at
     * compile time.
     */
    public static class Translator {

        private final MessageSource messageSource;
        private final Locale locale;

        Translator(MessageSource messageSource, Locale locale) {
            this.messageSource = messageSource;
            this.locale = locale;
        }

        public String get(String key) {
            return messageSource.getMessage(key, null, locale);
        }

        public String get(String key, Object argument) {
            return messageSource.getMessage(key, new Object[] { argument }, locale);
        }

        public Locale locale() {
            return locale;
        }
    }
}
