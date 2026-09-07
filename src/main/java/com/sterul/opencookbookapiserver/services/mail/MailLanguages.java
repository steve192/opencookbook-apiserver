package com.sterul.opencookbookapiserver.services.mail;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import lombok.extern.slf4j.Slf4j;

/**
 * Which of the translations a mail should be written in.
 *
 * There are two questions here and they have different answers. Writing to an account, the
 * account's own language wins, because the mail is usually composed long after the request that
 * caused it - a password reset lands in an inbox, not in a browser. Writing outside any account,
 * all there is to go on is what the client asked for.
 */
@Component
@Slf4j
public class MailLanguages {

    /**
     * What an account that has never expressed a preference gets. English rather than the
     * server's locale: an operator's machine settings say nothing about who reads the mail.
     */
    public static final Locale DEFAULT = Locale.ENGLISH;

    /**
     * Every language there is a bundle for, best first. Adding one here and adding
     * i18n/mail_xx.properties is the whole of adding a translation.
     */
    private static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.GERMAN);

    /**
     * The language to write to this account in: what it last told us, else what the client
     * asking right now wants, else the default.
     */
    public Locale forUser(CookpalUser user) {
        if (user != null) {
            var stored = supported(user.getLanguage());
            if (stored.isPresent()) {
                return stored.get();
            }
        }
        return forCurrentRequest();
    }

    /**
     * The language for a mail with no account behind it, taken from the client's Accept-Language.
     */
    public Locale forCurrentRequest() {
        return fromCurrentRequest().orElse(DEFAULT);
    }

    /**
     * What the client actually asked for, empty when it asked for nothing or for nothing we
     * have. Kept apart from {@link #forCurrentRequest()} so that storing a preference can tell
     * "this client wants English" from "this client said nothing" - only the first is worth
     * writing down.
     */
    public Optional<Locale> fromCurrentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            // A cron job or a test, with no browser anywhere in sight.
            return Optional.empty();
        }
        return fromAcceptLanguage(servletAttributes.getRequest().getHeader("Accept-Language"));
    }

    /**
     * The best supported match for an Accept-Language header, quality values and all, so that
     * "de-AT,de;q=0.9,en;q=0.5" finds German rather than falling through to the default.
     */
    public Optional<Locale> fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            var ranges = Locale.LanguageRange.parse(header);
            return Optional.ofNullable(Locale.lookup(ranges, SUPPORTED));
        } catch (IllegalArgumentException e) {
            // A malformed header is a client's problem, not a reason to fail sending a mail.
            log.debug("Ignoring unparseable Accept-Language header '{}'", header);
            return Optional.empty();
        }
    }

    /**
     * The translation a stored language tag refers to, empty when there is none - which is what
     * happens to a tag written by an older version that has since lost its bundle.
     */
    public Optional<Locale> supported(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return Optional.empty();
        }
        var wanted = Locale.forLanguageTag(languageTag).getLanguage();
        return SUPPORTED.stream()
                .filter(candidate -> candidate.getLanguage().equals(wanted))
                .findFirst();
    }
}
