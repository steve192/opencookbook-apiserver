package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * A subsystem is configured, but no token has been issued for it yet.
 *
 * The state between bringing the subsystem up and issuing its credential, and the one a
 * developer following the readme is in for a minute. It has to read as "not available" rather
 * than as available: the subsystem's health endpoint needs no token and would happily say it is
 * up, while every request that matters is refused.
 */
@SpringBootTest(properties = {
        "opencookbook.ml.service-url=http://127.0.0.1:1",
        "opencookbook.ml.api-token=",
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class MlWithoutATokenIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void theAppIsNotOfferedAFeatureThisInstanceCannotUse() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocrImportEnabled").value(false));
    }

    @Test
    @WithMockUser("test@test.com")
    void aScanIsRefusedAsUnavailableRatherThanFailingInside() throws Exception {
        TestUtils.whenAuthenticated(userRepository);

        // Without a token there is nothing to key the submitter id with either, and that has
        // to read the same way as the subsystem being down rather than as a server error.
        var photograph = new MockMultipartFile(
                "images", "page.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/ml/recipe-ocr").file(photograph))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(username = "operator@example.com", authorities = "ADMIN")
    void anOperatorCanStillSeeThatItIsNotAvailable() throws Exception {
        // The endpoints exist, because a subsystem is configured; only the credential is
        // missing, and that is exactly what an operator needs to be told.
        mockMvc.perform(get("/api/v1/admin/ml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
