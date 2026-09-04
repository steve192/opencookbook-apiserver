package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * An instance that has a subsystem configured, pointed at one that is not there.
 *
 * Nothing here needs a real subsystem: the point is that the endpoints exist, that requests
 * reach them, and that a subsystem which cannot be reached reads as the feature being
 * unavailable rather than as every scan being a mysterious error.
 */
@SpringBootTest(properties = {
        // Port 1 is reliably closed, so every call fails at connect.
        "opencookbook.ml.service-url=http://127.0.0.1:1",
        "opencookbook.ml.api-token=cpml_test_token",
        "opencookbook.ml.connect-timeout-seconds=1",
        "opencookbook.ml.request-timeout-seconds=1",
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class MlConfiguredIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser("test@test.com")
    void theScanEndpointExistsAndAcceptsPhotographs() throws Exception {
        TestUtils.whenAuthenticated(userRepository);

        // The subsystem is unreachable, so this is the "unavailable" answer rather than a 404.
        mockMvc.perform(multipart("/api/v1/ml/recipe-ocr").file(photograph()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser("test@test.com")
    void aScanWithNoPhotographIsRefusedBeforeAnythingIsSent() throws Exception {
        TestUtils.whenAuthenticated(userRepository);

        mockMvc.perform(multipart("/api/v1/ml/recipe-ocr"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser("test@test.com")
    void aCropInPixelsInsteadOfFractionsIsRefusedHere() throws Exception {
        TestUtils.whenAuthenticated(userRepository);

        mockMvc.perform(multipart("/api/v1/ml/recipe-ocr")
                .file(photograph())
                .param("payload", "{\"pages\":[{\"crop\":[[900,10],[1,0],[1,1],[0,1]]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser("test@test.com")
    void oneUsersJobCannotBeFoundByAnother() throws Exception {
        TestUtils.whenAuthenticated(userRepository);

        mockMvc.perform(get("/api/v1/ml/jobs/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnreachableSubsystemMeansTheFeatureIsReportedUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocrImportEnabled").value(false));
    }

    @Test
    @WithMockUser(username = "operator@example.com", authorities = "ADMIN")
    void anOperatorCanSeeThisInstancesUsage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").exists())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @WithMockUser(username = "operator@example.com", authorities = "ADMIN")
    void anOperatorCanSeeWhoHasUsedTodaysScanAllowance() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml/quota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").exists())
                .andExpect(jsonPath("$.users").isArray());
    }

    @Test
    @WithMockUser(username = "someone@example.com")
    void theQuotaViewIsNotForOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml/quota"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "someone@example.com")
    void norIsResettingSomebodysAllowance() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ml/quota/1/reset"))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile photograph() {
        return new MockMultipartFile(
                "images", "page.jpg", "image/jpeg", "not-really-a-jpeg".getBytes());
    }
}
