package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

import jakarta.mail.internet.MimeMessage;

/**
 * The language of a mail, over real http.
 *
 * The unit tests either side of this one take the language as given. What is checked here is the
 * one thing they cannot be: that the header a client sends actually arrives, is written down, and
 * comes back out as the language of the mail - including on the endpoints nobody is signed in
 * for, which are all of the ones that send a mail to somebody who is not currently looking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class MailLanguageIntegrationTest extends IntegrationTest {

    private static final String GERMAN_BROWSER = "de-AT,de;q=0.9,en;q=0.5";
    private static final String ENGLISH_BROWSER = "en-GB,en;q=0.9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    /** Mocked at the boundary, so that everything above it - templates included - really runs. */
    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    void aGermanBrowserSigningUpIsRememberedAndGetsAGermanMail() throws Exception {
        var address = signUp(GERMAN_BROWSER);

        assertEquals("de", userRepository.findByEmailAddress(address).getLanguage());
        assertEquals("Bestätige deinen CookPal Account", subjectOfSentMail());
    }

    @Test
    void aClientThatSaysNothingIsNotWrittenDownAndGetsTheDefault() throws Exception {
        // "said nothing" has to stay distinguishable from "asked for English", or an account can
        // never follow a later change of default.
        var address = signUp(null);

        assertNull(userRepository.findByEmailAddress(address).getLanguage());
        assertEquals("Confirm your CookPal account", subjectOfSentMail());
    }

    @Test
    void signingInFromAPhoneInAnotherLanguageChangesIt() throws Exception {
        var address = signUp(ENGLISH_BROWSER);
        activate(address);

        logIn(address, GERMAN_BROWSER);

        assertEquals("de", userRepository.findByEmailAddress(address).getLanguage());
    }

    @Test
    void aPasswordResetIsWrittenInTheAccountsLanguageNotTheBrowsersAtTheLoginScreen() throws Exception {
        // Nobody is signed in for this one, and the browser asking is not necessarily the phone
        // the account is used from. The account is the better answer of the two.
        var address = signUp(GERMAN_BROWSER);

        mockMvc.perform(post("/api/v1/users/requestPasswordReset")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", ENGLISH_BROWSER)
                .content("{\"emailAddress\":\"" + address + "\"}"))
                .andExpect(status().isOk());

        assertEquals("Setze dein CookPal Passwort zurück", subjectOfSentMail());
    }

    @Test
    void aMailReallyLeavesWithBothPartsAndTheLogoInside() throws Exception {
        signUp(GERMAN_BROWSER);

        var raw = new ByteArrayOutputStream();
        lastSentMail().writeTo(raw);
        assertTrue(raw.toString().contains("multipart/alternative"), "there is no plain text alternative");
        assertTrue(raw.toString().contains("Content-ID: <cookpal-logo>"), "the logo did not travel with it");
    }

    private String signUp(String acceptLanguage) throws Exception {
        when(javaMailSender.createMimeMessage()).thenAnswer(call -> new JavaMailSenderImpl().createMimeMessage());

        var address = UUID.randomUUID() + "@cookpal.invalid";
        var request = post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + address + "\",\"password\":\"a-password\"}");
        if (acceptLanguage != null) {
            request = request.header("Accept-Language", acceptLanguage);
        }
        mockMvc.perform(request).andExpect(status().isOk());
        return address;
    }

    private void activate(String address) {
        var user = userRepository.findByEmailAddress(address);
        user.setActivated(true);
        userRepository.save(user);
    }

    private void logIn(String address, String acceptLanguage) throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", acceptLanguage)
                .content("{\"emailAddress\":\"" + address + "\",\"password\":\"a-password\"}"))
                .andExpect(status().isOk());
    }

    private String subjectOfSentMail() throws Exception {
        return lastSentMail().getSubject();
    }

    private MimeMessage lastSentMail() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }
}
