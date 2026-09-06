package com.sterul.opencookbookapiserver.unit.services.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;

/**
 * Which language a mail comes out in.
 *
 * Worth pinning because the wrong answer is invisible from here: nothing fails, somebody just
 * gets a mail they cannot read. The distinction that matters most is between a client that asked
 * for English and one that asked for nothing - only the first should ever be written down.
 */
class MailLanguagesTest {

    private final MailLanguages cut = new MailLanguages();

    @AfterEach
    void forgetTheRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void anAccountIsWrittenToInTheLanguageItLastAskedFor() {
        assertEquals(Locale.GERMAN, cut.forUser(userWithLanguage("de")));
    }

    @Test
    void theAccountWinsOverTheBrowserAskingRightNow() {
        // A password reset is composed while the browser is still there, but the account knows
        // better than a machine somebody happens to be sitting at.
        givenARequestAsking("en-GB,en;q=0.9");

        assertEquals(Locale.GERMAN, cut.forUser(userWithLanguage("de")));
    }

    @Test
    void anAccountThatNeverSaidAnythingFollowsTheBrowser() {
        givenARequestAsking("de-AT,de;q=0.9,en;q=0.5");

        assertEquals(Locale.GERMAN, cut.forUser(userWithLanguage(null)));
    }

    @Test
    void anAccountAskingForALanguageWeDoNotHaveGetsTheDefault() {
        // A tag left over from a translation that has since been removed.
        assertEquals(MailLanguages.DEFAULT, cut.forUser(userWithLanguage("fr")));
    }

    @Test
    void withNoAccountAndNoRequestTheDefaultIsEnglish() {
        assertEquals(Locale.ENGLISH, cut.forCurrentRequest());
        assertEquals(Locale.ENGLISH, MailLanguages.DEFAULT);
    }

    @Test
    void qualityValuesDecideBetweenTwoLanguagesWeHave() {
        assertEquals(Locale.GERMAN, cut.fromAcceptLanguage("en;q=0.3,de;q=0.8").orElseThrow());
        assertEquals(Locale.ENGLISH, cut.fromAcceptLanguage("de;q=0.3,en;q=0.8").orElseThrow());
    }

    @Test
    void aLanguageWeDoNotHaveIsNotAnAnswer() {
        // Empty rather than English, so that nothing writes "en" down on the strength of a
        // client that never mentioned it.
        assertTrue(cut.fromAcceptLanguage("fr-FR,fr;q=0.9").isEmpty());
        assertTrue(cut.fromAcceptLanguage("").isEmpty());
        assertTrue(cut.fromAcceptLanguage(null).isEmpty());
    }

    @ParameterizedTest(name = "{0} is German")
    @ValueSource(strings = {
            "de",
            "de-DE",
            "de-AT",
            "de-CH-1901",              // variant subtag
            "de-DE-u-co-phonebk",      // unicode extension
            "de-DE-x-a-private-use",   // private use subtag
            "*;q=0.5,de;q=0.1",        // a wildcard alongside something we have
    })
    void aTagCarryingScriptsVariantsOrExtensionsStillFindsItsLanguage(String header) {
        // Tags get long, and the interesting part is at the front. RFC 4647 lookup drops
        // subtags from the right until something matches, which is what makes this work
        // without listing every region and variant German is spoken in.
        assertEquals(Locale.GERMAN, cut.fromAcceptLanguage(header).orElseThrow());
    }

    @ParameterizedTest(name = "{0} is nobody we have")
    @ValueSource(strings = {
            "zh-Hant-TW",                       // a language with no bundle
            "sr-Latn-RS",
            "gsw-u-sd-chzh",                    // swiss german is not German here
            "deu",                              // iso 639-2 rather than 639-1
            "i-klingon",                        // legacy grandfathered tag
            "x-pig-latin",                      // private use only
            "*",                                // wildcard only
            "de_DE",                            // underscores, which is not the syntax
            ";;;;",
            "de;q=abc",                         // unparseable quality value
            "averylongsubtagwellbeyondthelimit",
            "  ",
    })
    void anythingElseFallsBackInsteadOfFailing(String header) {
        // Empty rather than English: "asked for something we do not have" and "asked for
        // English" must stay distinguishable, because only the first should never be stored.
        assertTrue(cut.fromAcceptLanguage(header).isEmpty(), header + " should not have matched");
        // But a mail still has to come out in something.
        assertEquals(MailLanguages.DEFAULT, cut.forUser(userWithLanguage(null)));
    }

    @Test
    void onlyALanguageWeActuallyHaveCanEverBeWrittenDown() {
        // The value stored on an account never comes from the client, it comes from the list of
        // translations - so no header, however exotic, can put an unknown tag in the column.
        for (var header : new String[] { "de-DE-u-co-phonebk", "en-GB", "zh-Hant-TW", "*" }) {
            cut.fromAcceptLanguage(header)
                    .ifPresent(language -> assertTrue(language.getLanguage().length() <= 3
                            && cut.supported(language.getLanguage()).isPresent(),
                            header + " produced something unstorable: " + language));
        }
    }

    @Test
    void anAbsurdlyLongHeaderIsAnsweredRatherThanChokedOn() {
        // Far longer than any real client sends, and longer than the container would even
        // accept - but the parsing must not be where that gets interesting.
        var many = new StringBuilder();
        for (var i = 0; i < 4000; i++) {
            many.append("xx").append((char) ('a' + i % 26)).append(";q=0.5,");
        }
        many.append("de");

        assertEquals(Locale.GERMAN, cut.fromAcceptLanguage(many.toString()).orElseThrow());
    }

    @Test
    void aRequestWithoutTheHeaderSaysNothing() {
        givenARequestAsking(null);

        assertTrue(cut.fromCurrentRequest().isEmpty());
    }

    private CookpalUser userWithLanguage(String language) {
        var user = new CookpalUser();
        user.setEmailAddress("someone@cookpal.invalid");
        user.setLanguage(language);
        return user;
    }

    private void givenARequestAsking(String acceptLanguage) {
        var request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
