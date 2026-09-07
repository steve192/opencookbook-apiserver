package com.sterul.opencookbookapiserver.services.mail;

import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

/**
 * Turns a mail into the two documents that get sent: the html one and the plain text one.
 *
 * Both are always produced. A mail with nothing but html in it reads as spam to a fair number of
 * filters, and the text part is also what a watch, a screen reader or a terminal client shows.
 *
 * The html half is a body template poured into a shared layout, both written against the macros
 * in mailtemplates/macros.vm. That is what keeps three mails looking like one product: a body
 * template says {@code #button(...)}, and what a button is lives in exactly one place.
 */
@Component
public class MailRenderer {

    /**
     * How the layout addresses the logo it carries with it (as {@code cid:...}). The mail brings
     * its own picture rather than linking to one, because a client that blocks remote images -
     * which is most of them, by default - would otherwise show a blank where the brand is.
     */
    public static final String LOGO_CONTENT_ID = "cookpal-logo";

    public static final String LOGO_RESOURCE = "mailtemplates/images/cookpal-logo.png";

    private final VelocityEngine velocityEngine;
    private final MailMessages messages;
    private final OpencookbookConfiguration configuration;

    public MailRenderer(@Qualifier("mailVelocityEngine") VelocityEngine velocityEngine, MailMessages messages,
            OpencookbookConfiguration configuration) {
        this.velocityEngine = velocityEngine;
        this.messages = messages;
        this.configuration = configuration;
    }

    /** An addressed, translated mail, ready to be handed to a mail server. */
    public record RenderedMail(String subject, String html, String text) {
    }

    /**
     * @param kind      which mail, which decides both the templates and the texts
     * @param locale    the language to write it in
     * @param recipient the address it goes to, shown in the footer so that a forwarded mail
     *                  still says who it was for
     * @param model     the values the body template needs, typically one link
     */
    public RenderedMail render(MailKind kind, Locale locale, String recipient, Map<String, Object> model) {
        var i18n = messages.forLocale(locale);
        var subject = i18n.get(kind.subjectKey());
        var preheader = i18n.get(kind.preheaderKey());

        // The recipient is the one value in a mail that somebody else chose, and it is the only
        // reason this escapes anything at all.
        var html = renderPart("html", kind, i18n, subject, preheader, escapeHtml(recipient), model);
        var text = renderPart("text", kind, i18n, subject, preheader, recipient, model);

        return new RenderedMail(subject, html, text.strip() + "\n");
    }

    private String renderPart(String flavour, MailKind kind, MailMessages.Translator i18n, String subject,
            String preheader, String recipient, Map<String, Object> model) {
        var context = new VelocityContext(new HashMap<>(model));
        context.put("i18n", i18n);
        context.put("lang", i18n.locale().getLanguage());
        context.put("title", subject);
        context.put("preheader", preheader);
        context.put("recipient", recipient);
        context.put("logoContentId", LOGO_CONTENT_ID);
        context.put("instanceUrl", emptyToNull(configuration.getInstanceURL()));
        context.put("instanceHost", instanceHost());

        // The body first, then the shell around it, so that the layout is written once and a
        // body template never has to know it is inside anything.
        context.put("body", merge("mailtemplates/" + flavour + "/" + kind.templateName() + ".vm", context));
        return merge("mailtemplates/" + flavour + "/layout.vm", context);
    }

    private String merge(String templatePath, VelocityContext context) {
        var writer = new StringWriter();
        velocityEngine.getTemplate(templatePath, StandardCharsets.UTF_8.name()).merge(context, writer);
        return writer.toString();
    }

    /**
     * The bare host of this instance, for a footer link that reads "cookpal.io" rather than
     * repeating the whole address. Falls back to the configured value when it is not a url an
     * operator can be talked out of.
     */
    private String instanceHost() {
        var url = emptyToNull(configuration.getInstanceURL());
        if (url == null) {
            return null;
        }
        try {
            var host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /** Velocity's {@code #if} treats an empty string as true, so unset has to mean null. */
    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
