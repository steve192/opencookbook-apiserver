package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestAccounts.operator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;

/**
 * An SMTP host but no instance address: no mail is sent, because its links would be built from
 * the Host header of whoever asked for it.
 */
@SpringBootTest(properties = "opencookbook.smtp-host=smtp.cookpal.invalid")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class MailWithoutInstanceUrlIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        TestInstance.setUp(userRepository);
    }

    @Test
    void theInstanceSaysMailIsNotEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/instance")).andExpect(jsonPath("$.mailEnabled").value(false));
    }

    @Test
    void theOverviewSaysMailIsConfiguredButNotEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/admin/instance").with(operator(TestInstance.ADMINISTRATOR)))
                .andExpect(jsonPath("$.mail.configured").value(true))
                .andExpect(jsonPath("$.mail.enabled").value(false))
                .andExpect(jsonPath("$.registration.confirmation").value("ADMIN_APPROVAL"));
    }

    @Test
    void aForgedHostCannotMakeTheServerMailALink() throws Exception {
        mockMvc.perform(post("/api/v1/users/requestPasswordReset")
                .header("Host", "evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + TestInstance.ADMINISTRATOR + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));

        verify(emailService, never()).sendPasswordResetMail(any());
    }

    @Test
    void anOpenSignupWaitsForAnAdministrator() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                .header("Host", "evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"someone@example.com\",\"password\":\"a-password\"}"))
                .andExpect(jsonPath("$.state").value("AWAITING_APPROVAL"));

        verify(emailService, never()).sendActivationMail(any());
    }
}
