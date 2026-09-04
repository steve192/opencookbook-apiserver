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
 * An instance with no machine learning subsystem, which is the default and the common case.
 *
 * Configuring no url takes the endpoints away rather than making them refuse, so there is no
 * code path left that could reach a subsystem that was never set up - and the app is told once,
 * through the instance info, instead of finding out one failed scan at a time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class MlDisabledIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} {1} is gone")
    @CsvSource({
            "POST,   /api/v1/ml/recipe-ocr",
            "GET,    /api/v1/ml/jobs/any-job",
            "DELETE, /api/v1/ml/jobs/any-job",
            "DELETE, /api/v1/ml/training-data",
    })
    @WithMockUser("somebody@example.com")
    void nobodyCanScanARecipe(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "operator@example.com", authorities = "ADMIN")
    void thereAreNoMachineLearningStatisticsToAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theInstanceSaysSoUpFrontSoTheAppCanStopOfferingIt() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocrImportEnabled").value(false));
    }

    @Test
    void theRestOfTheInstanceIsUnaffected() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingEnabled").value(true));
    }
}
