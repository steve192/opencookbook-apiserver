package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** The first-run screen is shown once; clearing the name later does not bring it back. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class UserOnboardingIntegrationTest extends IntegrationTestBase {

    private static final String NEWCOMER = "newcomer@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        var existing = userRepository.findByEmailAddress(NEWCOMER);
        if (existing != null) {
            userRepository.delete(existing);
        }
        var user = new CookpalUser();
        user.setEmailAddress(NEWCOMER);
        user.setPasswordHash("irrelevant");
        user.setActivated(true);
        userRepository.save(user);
    }

    @Test
    @WithMockUser(username = NEWCOMER)
    void anAccountThatHasNeverBeenSetUpSaysSo() throws Exception {
        mockMvc.perform(get("/api/v1/users/self"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(false))
                .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    @WithMockUser(username = NEWCOMER)
    void finishingTheSetupTakesTheNameAndIsRememberedAfterwards() throws Exception {
        mockMvc.perform(post("/api/v1/users/self/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Anna\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(true))
                .andExpect(jsonPath("$.displayName").value("Anna"));

        mockMvc.perform(get("/api/v1/users/self"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(true));
    }

    @Test
    @WithMockUser(username = NEWCOMER)
    void clearingTheNameAfterwardsDoesNotAskAgain() throws Exception {
        mockMvc.perform(post("/api/v1/users/self/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Anna\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/self/displayName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.onboarded").value(true));
    }
}
