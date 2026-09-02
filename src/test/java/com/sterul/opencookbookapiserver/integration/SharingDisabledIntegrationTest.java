package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An instance that does not want to publish anything.
 *
 * Turning sharing off takes the endpoints away rather than making them refuse, so links already
 * handed out stop resolving and there is no code path left that could serve one by mistake.
 */
@SpringBootTest(properties = "opencookbook.sharing.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class SharingDisabledIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} {1} is gone")
    @CsvSource({
            "GET,    /api/v1/shared/any-share",
            "GET,    /api/v1/shared/any-share/images/any-image",
            "GET,    /api/v1/shared/any-share/images/thumbnail/any-image",
    })
    void sharedRecipesCannotBeReached(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0} {1} is gone")
    @CsvSource({
            "POST,   /api/v1/shares",
            "GET,    /api/v1/shares",
            "DELETE, /api/v1/shares/any-share",
            "POST,   /api/v1/shares/any-share/import",
    })
    @WithMockUser("somebody@example.com")
    void nobodyCanCreateOrUseAShare(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isNotFound());
    }

    @Test
    void theInstanceSaysSoUpFrontSoTheAppCanStopOfferingIt() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingEnabled").value(false));
    }

    @Test
    @WithMockUser(username = "operator@example.com", authorities = "ADMIN")
    void anOperatorCanStillCleanUpWhatWasSharedBefore() throws Exception {
        // The shares themselves are kept, so the way to remove them has to outlive the switch.
        mockMvc.perform(get("/api/v1/admin/shares"))
                .andExpect(status().isOk());
    }
}
