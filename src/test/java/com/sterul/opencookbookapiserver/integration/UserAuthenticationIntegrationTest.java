package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class UserAuthenticationIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivationLinkRepository activationLinkRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @Test
    void userCanSignUpActivateAndLogin() throws Exception {
        var credentials = newCredentials();

        signUp(credentials.emailAddress(), credentials.password())
                .andExpect(status().isOk());

        var createdUser = userRepository.findByEmailAddress(credentials.emailAddress());
        assertNotNull(createdUser);
        assertFalse(createdUser.isActivated());
        assertTrue(passwordEncoder.matches(credentials.password(), createdUser.getPasswordHash()));

        login(credentials.emailAddress(), credentials.password())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.userActive").value(false));

        var activationLink = findActivationLinkForUser(credentials.emailAddress());

        activate(activationLink.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userActive").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").isString());

        var activatedUser = userRepository.findByEmailAddress(credentials.emailAddress());
        assertTrue(activatedUser.isActivated());
        assertTrue(activationLinkRepository.findById(activationLink.getId()).isEmpty());

        login(credentials.emailAddress(), credentials.password())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userActive").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        var credentials = newCredentials();

        signUp(credentials.emailAddress(), credentials.password())
                .andExpect(status().isOk());

        login(credentials.emailAddress(), credentials.password())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.userActive").value(false))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void loginWithWrongPasswordFailsForActiveUser() throws Exception {
        var credentials = newCredentials();

        signUp(credentials.emailAddress(), credentials.password())
                .andExpect(status().isOk());

        activate(findActivationLinkForUser(credentials.emailAddress()).getId())
                .andExpect(status().isOk());

        login(credentials.emailAddress(), "wrong-password")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownEmailFails() throws Exception {
        login("missing-" + UUID.randomUUID() + "@example.com", "wrong-password")
                .andExpect(status().isUnauthorized());
    }

    private Credentials newCredentials() {
        return new Credentials("auth-" + UUID.randomUUID() + "@example.com", "test-password");
    }

    private ResultActions signUp(String emailAddress, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "emailAddress": "%s",
                          "password": "%s"
                        }
                        """.formatted(emailAddress, password)));
    }

    private ResultActions login(String emailAddress, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "emailAddress": "%s",
                          "password": "%s"
                        }
                        """.formatted(emailAddress, password)));
    }

    private ResultActions activate(String activationId) throws Exception {
        return mockMvc.perform(get("/api/v1/users/activate")
                .param("activationId", activationId));
    }

    private ActivationLink findActivationLinkForUser(String emailAddress) {
        return activationLinkRepository.findAll().stream()
                .filter(link -> emailAddress.equals(link.getUser().getEmailAddress()))
                .findFirst()
                .orElseThrow();
    }

    private record Credentials(String emailAddress, String password) {
    }
}
