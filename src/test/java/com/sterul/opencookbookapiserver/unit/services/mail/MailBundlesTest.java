package com.sterul.opencookbookapiserver.unit.services.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import com.sterul.opencookbookapiserver.configurations.EmailConfiguration;

/**
 * The translations as a set, rather than one mail at a time.
 *
 * A key added to English and forgotten in German does not break anything loudly - the mail goes
 * out with one English sentence in the middle of a German one, and nobody hears about it. That is
 * the failure this guards; the fallback below is only the safety net under it.
 */
class MailBundlesTest {

    private static final List<Locale> TRANSLATIONS = List.of(Locale.GERMAN);

    private final MessageSource messageSource = new EmailConfiguration().mailMessageSource();

    @Test
    void everyTranslationSaysEverythingEnglishSays() throws IOException {
        var english = keysOf("i18n/mail.properties");

        for (var translation : TRANSLATIONS) {
            var translated = keysOf("i18n/mail_" + translation.getLanguage() + ".properties");

            var missing = new TreeSet<>(english);
            missing.removeAll(translated);
            assertTrue(missing.isEmpty(), translation + " is missing " + missing);

            var extra = new TreeSet<>(translated);
            extra.removeAll(english);
            assertTrue(extra.isEmpty(), translation + " has keys English does not: " + extra);
        }
    }

    @Test
    void noTextWasLeftEmptyOrLeftInEnglish() throws IOException {
        var english = load("i18n/mail.properties");

        for (var translation : TRANSLATIONS) {
            var translated = load("i18n/mail_" + translation.getLanguage() + ".properties");

            for (var key : translated.stringPropertyNames()) {
                assertFalse(translated.getProperty(key).isBlank(), key + " is empty in " + translation);
                // The product's own name is the same in every language; nothing else should be.
                if (!key.equals("mail.app.name")) {
                    assertFalse(english.getProperty(key).equals(translated.getProperty(key)),
                            key + " was never translated into " + translation);
                }
            }
        }
    }

    @Test
    void aLanguageWeDoNotHaveIsAnsweredInEnglishRatherThanInTheServersOwnLanguage() {
        // The operator's machine settings say nothing about who reads the mail, so an unknown
        // language has to land on English and not on whatever the jvm happens to be running as.
        var previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);

            assertEquals("Confirm your CookPal account",
                    messageSource.getMessage("mail.activation.subject", null, Locale.JAPANESE));
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void aRegionalVariantIsAnsweredByItsLanguage() {
        assertEquals("Bestätige deinen CookPal Account",
                messageSource.getMessage("mail.activation.subject", null, Locale.forLanguageTag("de-AT")));
    }

    private static TreeSet<String> keysOf(String resource) throws IOException {
        return new TreeSet<>(load(resource).stringPropertyNames());
    }

    private static Properties load(String resource) throws IOException {
        var properties = new Properties();
        try (var stream = MailBundlesTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException(resource + " is not on the classpath");
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
