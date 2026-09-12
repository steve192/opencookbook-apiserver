package com.sterul.opencookbookapiserver.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sterul.opencookbookapiserver.services.EmailService;

/**
 * The endpoints anybody can reach without a token.
 *
 * They are the only surface a stranger has, and two of them make the server send mail, so what
 * is checked here is that both budgets are actually applied - and that neither of them changes
 * what an endpoint answers about whether an account exists.
 *
 * Each test uses an address and an inbox of its own, because the counters are shared by
 * everything in the context and tests must not spend each other's budgets.
 */
@SpringBootTest(properties = {
        "opencookbook.auth.attempts-per-hour-per-ip=3",
        "opencookbook.auth.mails-per-hour-per-address=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AuthRateLimitIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void guessingIsRefusedOnceTheBudgetIsSpent() throws Exception {
        var client = fromClient("203.0.113.10");

        for (var attempt = 0; attempt < 3; attempt++) {
            login("nobody@example.com", "wrong", client)
                    .andExpect(status().isUnauthorized());
        }

        login("nobody@example.com", "wrong", client)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void oneCallerRunningOutDoesNotShutOutTheRest() throws Exception {
        var exhausted = fromClient("203.0.113.11");
        for (var attempt = 0; attempt < 4; attempt++) {
            login("nobody@example.com", "wrong", exhausted);
        }

        login("nobody@example.com", "wrong", fromClient("203.0.113.12"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Renewing a token is left out of the budget on purpose: the app does it every few minutes,
     * so everybody behind one address would otherwise be locked out in normal use.
     */
    @Test
    void renewingATokenIsNotCountedAgainstTheBudget() throws Exception {
        var client = fromClient("203.0.113.13");
        for (var attempt = 0; attempt < 4; attempt++) {
            login("nobody@example.com", "wrong", client);
        }

        mockMvc.perform(post("/api/v1/users/refreshToken")
                .with(client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "not-a-real-token"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The budget that stops this server being used to post mail to somebody who never asked for
     * it. Every attempt still answers 200, because answering differently once the budget was
     * spent would say whether the address belongs to an account.
     */
    @Test
    void aninboxCannotBeFilledFromHere() throws Exception {
        var address = "flood-" + UUID.randomUUID() + "@example.com";
        signUp(address, fromClient("203.0.113.14"));

        // Each from a different address, so that only the per-recipient budget is in play -
        // which is the one that matters here, since an attacker with many addresses is exactly
        // what a per-caller budget alone would miss.
        for (var attempt = 0; attempt < 4; attempt++) {
            requestPasswordReset(address, fromClient("198.51.100." + attempt))
                    .andExpect(status().isOk());
        }

        // Two of the four, and the signup mail is not one of them: it goes out before any reset
        // is asked for and is counted separately.
        verify(emailService, times(2)).sendPasswordResetMail(any());
    }

    @Test
    void anAddressNobodyRegisteredIsNeverWrittenToAtAll() throws Exception {
        requestPasswordReset("stranger-" + UUID.randomUUID() + "@example.com",
                fromClient("203.0.113.16"))
                .andExpect(status().isOk());

        verify(emailService, never()).sendPasswordResetMail(any());
    }

    private ResultActions login(String emailAddress, String password, RequestPostProcessor client)
            throws Exception {
        return mockMvc.perform(post("/api/v1/users/login")
                .with(client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress": "%s", "password": "%s"}
                        """.formatted(emailAddress, password)));
    }

    private ResultActions signUp(String emailAddress, RequestPostProcessor client)
            throws Exception {
        return mockMvc.perform(post("/api/v1/users/signup")
                .with(client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress": "%s", "password": "test-password"}
                        """.formatted(emailAddress)))
                .andExpect(status().isOk());
    }

    private ResultActions requestPasswordReset(String emailAddress, RequestPostProcessor client)
            throws Exception {
        return mockMvc.perform(post("/api/v1/users/requestPasswordReset")
                .with(client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress": "%s"}
                        """.formatted(emailAddress)));
    }

    private static RequestPostProcessor fromClient(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
