package com.sterul.opencookbookapiserver.unit.services.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sterul.opencookbookapiserver.configurations.EmailConfiguration;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.mail.MailKind;
import com.sterul.opencookbookapiserver.services.mail.MailMessages;
import com.sterul.opencookbookapiserver.services.mail.MailRenderer;

/**
 * What actually comes out of the templates.
 *
 * These are cheap checks for the mistakes that survive a code review and only show up in
 * somebody's inbox: a template renaming a variable the sender still fills under the old name (so
 * the mail arrives saying "$activationLink"), a key that exists in English and not in German, a
 * mail that lost its plain text half.
 *
 * It also writes every mail to target/mail-previews so that the design can be looked at in a
 * browser, or pasted into a client testing service, without sending anything.
 */
class MailRendererTest {

    private static final String RECIPIENT = "someone@cookpal.invalid";
    private static final Path PREVIEW_DIR = Path.of("target", "mail-previews");

    private final OpencookbookConfiguration configuration = configuration();
    private final MailRenderer cut = new MailRenderer(
            new EmailConfiguration().mailVelocityEngine(),
            new MailMessages(new EmailConfiguration().mailMessageSource()),
            configuration);

    static Stream<Arguments> everyMailInEveryLanguage() {
        return Stream.of(Locale.ENGLISH, Locale.GERMAN)
                .flatMap(locale -> Stream.of(MailKind.values()).map(kind -> Arguments.of(kind, locale)));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyMailInEveryLanguage")
    void everyMailRendersCompletely(MailKind kind, Locale locale) throws IOException {
        var mail = cut.render(kind, locale, RECIPIENT, model(kind));

        assertFalse(mail.subject().isBlank(), "the subject is empty");
        assertTrue(mail.html().contains("<html"), "the html part is not a document");
        assertFalse(mail.text().isBlank(), "the plain text part is empty");

        // Velocity leaves an unresolved reference in the output verbatim, which is exactly how a
        // renamed variable reaches an inbox looking like "$activationLink".
        assertFalse(mail.html().contains("${"), "the html part has an unresolved reference");
        assertFalse(mail.text().contains("${"), "the text part has an unresolved reference");
        assertFalse(mail.html().contains("$i18n"), "the html part has an unresolved reference");
        assertFalse(mail.text().contains("$i18n"), "the text part has an unresolved reference");
        // A key with no entry in the bundle renders as its own name.
        assertFalse(mail.html().contains("mail." + kind.templateName() + "."), "a text is missing from the bundle");

        writePreview(kind, locale, mail);
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyMailInEveryLanguage")
    void everyMailCarriesTheBrandAndTheFooter(MailKind kind, Locale locale) {
        var mail = cut.render(kind, locale, RECIPIENT, model(kind));

        assertTrue(mail.html().contains("cid:cookpal-logo"), "the logo is not referenced");
        assertTrue(mail.html().contains(RECIPIENT), "the footer does not say who this was for");
        assertTrue(mail.text().contains(RECIPIENT), "the footer does not say who this was for");
        assertTrue(mail.html().contains("https://cookpal.example"), "the instance is not linked");
    }

    @Test
    void theLinkOfALinkMailIsInBothParts() {
        var activation = cut.render(MailKind.ACTIVATION, Locale.ENGLISH, RECIPIENT, model(MailKind.ACTIVATION));
        assertTrue(activation.html().contains("https://cookpal.example/activateAccount?activationId=abc"));
        assertTrue(activation.text().contains("https://cookpal.example/activateAccount?activationId=abc"));

        var reset = cut.render(MailKind.PASSWORD_RESET, Locale.ENGLISH, RECIPIENT, model(MailKind.PASSWORD_RESET));
        assertTrue(reset.html().contains("https://cookpal.example/resetPassword?id=abc"));
        assertTrue(reset.text().contains("https://cookpal.example/resetPassword?id=abc"));
    }

    @Test
    void theTranslationsAreActuallyDifferent() {
        var english = cut.render(MailKind.ACTIVATION, Locale.ENGLISH, RECIPIENT, model(MailKind.ACTIVATION));
        var german = cut.render(MailKind.ACTIVATION, Locale.GERMAN, RECIPIENT, model(MailKind.ACTIVATION));

        assertEquals("Confirm your CookPal account", english.subject());
        assertEquals("Bestätige deinen CookPal Account", german.subject());
        assertTrue(german.html().contains("Willkommen bei CookPal"));
        // The bundles are read as UTF-8, which is the difference between "Bestätige" and the
        // mojibake a default-encoded properties file would produce.
        assertTrue(german.text().contains("Küche") || german.html().contains("Küche"));
    }

    @Test
    void aLanguageWithNoBundleFallsBackToEnglishRatherThanFailing() {
        var mail = cut.render(MailKind.ACTIVATION, Locale.FRENCH, RECIPIENT, model(MailKind.ACTIVATION));

        assertEquals("Confirm your CookPal account", mail.subject());
    }

    @Test
    void withNoInstanceAddressTheFooterLinkIsLeftOutRatherThanBroken() {
        configuration.setInstanceURL("");

        var mail = cut.render(MailKind.ACCOUNT_DELETED, Locale.ENGLISH, RECIPIENT, Map.of());

        assertFalse(mail.html().contains("href=\"\""), "an empty link was rendered");
        assertFalse(mail.text().contains("$instanceUrl"));
    }

    @Test
    void theHtmlPartDoesNotDependOnAStyleBlock() {
        // Gmail's mobile apps drop <style> for accounts that are not Gmail accounts, so anything
        // load bearing has to be inline. This checks the shape rather than the styling: the card,
        // the button and the text all carry their own style attribute.
        var mail = cut.render(MailKind.ACTIVATION, Locale.ENGLISH, RECIPIENT, model(MailKind.ACTIVATION));
        var withoutStyleBlock = mail.html().replaceAll("(?s)<style.*?</style>", "");

        assertTrue(withoutStyleBlock.contains("background-color:#72B600"), "the button lost its colour");
        assertTrue(withoutStyleBlock.contains("background-color:#FFFFFF"), "the card lost its background");
        assertTrue(withoutStyleBlock.contains("font-family:"), "the text lost its font");
    }

    private Map<String, Object> model(MailKind kind) {
        return switch (kind) {
            case ACTIVATION -> Map.of("activationLink", "https://cookpal.example/activateAccount?activationId=abc");
            case PASSWORD_RESET -> Map.of("resetLink", "https://cookpal.example/resetPassword?id=abc");
            case ACCOUNT_DELETED -> Map.of();
        };
    }

    private static OpencookbookConfiguration configuration() {
        var configuration = new OpencookbookConfiguration();
        configuration.setInstanceURL("https://cookpal.example");
        return configuration;
    }

    private void writePreview(MailKind kind, Locale locale, MailRenderer.RenderedMail mail) throws IOException {
        Files.createDirectories(PREVIEW_DIR);
        var name = kind.templateName() + "." + locale.getLanguage();
        // The preview is opened in a browser, which has no idea what cid: means.
        var html = mail.html().replace("cid:cookpal-logo",
                "data:image/png;base64," + Base64Logo.INSTANCE);
        Files.writeString(PREVIEW_DIR.resolve(name + ".html"), html, StandardCharsets.UTF_8);
        Files.writeString(PREVIEW_DIR.resolve(name + ".txt"),
                "Subject: " + mail.subject() + "\n\n" + mail.text(), StandardCharsets.UTF_8);
    }

    /** The logo, so that a preview in a browser shows the mail as it is actually received. */
    private static final class Base64Logo {
        private static final String INSTANCE = load();

        private static String load() {
            try (var stream = MailRendererTest.class.getClassLoader()
                    .getResourceAsStream(MailRenderer.LOGO_RESOURCE)) {
                return java.util.Base64.getEncoder().encodeToString(stream.readAllBytes());
            } catch (IOException | NullPointerException e) {
                throw new IllegalStateException("The mail logo is missing from the classpath", e);
            }
        }
    }
}
