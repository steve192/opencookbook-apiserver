package com.sterul.opencookbookapiserver.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sterul.opencookbookapiserver.services.EmailService;

/**
 * An address is checked where one is stored or posted to, so that nothing further down - a mail
 * header, the log, a column of 255 characters - has to cope with whatever was sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class EmailValidationIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @ParameterizedTest
    @ValueSource(strings = {
        "not an address",
        "missing-the-at.example.com",
        "someone@",
        "@example.com",
        "someone@example.com\nInjected: header",
    })
    void anAddressThatIsNotOneCannotBeSignedUpWith(String address) throws Exception {
        signUp(address)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("emailAddress"));
    }

    /** The column holds 255 characters, so a longer one has to be refused, not attempted. */
    @Test
    void anAddressLongerThanTheColumnIsRefusedRatherThanFailingOnInsert() throws Exception {
        signUp("a".repeat(250) + "@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void anOrdinaryAddressIsStillAccepted() throws Exception {
        signUp("someone-" + java.util.UUID.randomUUID() + "@example.com")
                .andExpect(status().isOk());
    }

    /** The endpoints that send mail check too, so nothing shaped like a header reaches one. */
    @Test
    void anAddressThatIsNotOneIsNotWrittenTo() throws Exception {
        mockMvc.perform(post("/api/v1/users/requestPasswordReset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress": "someone@example.com\\nBcc: somebody@example.org"}
                        """))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendPasswordResetMail(any());
    }

    private ResultActions signUp(String emailAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\": %s, \"password\": \"test-password\"}"
                        .formatted(quoted(emailAddress))));
    }

    /** Sent as json, so a newline in the address has to survive the encoding to be tested. */
    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
