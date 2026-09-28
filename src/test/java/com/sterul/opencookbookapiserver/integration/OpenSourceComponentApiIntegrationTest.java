package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** What the app's open-source licenses screen shows of the server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class OpenSourceComponentApiIntegrationTest extends IntegrationTestBase {

    private static final String READER = "licenses@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        TestAccounts.ensure(userRepository, READER);
    }

    @Test
    void theServerAndTheCatalogueDataAreListedWithoutVersions() throws Exception {
        mockMvc.perform(get("/api/v1/open-source-components").with(user(READER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].id").value("server"))
                .andExpect(jsonPath("$.sections[1].id").value("catalogue"))
                .andExpect(jsonPath("$.sections[1].components[?(@.name == 'Bundeslebensmittelschlüssel (BLS)')].license")
                        .value("CC BY 4.0"))
                .andExpect(jsonPath("$.sections[1].components[0].licenseUrl").value("https://creativecommons.org/licenses/by/4.0/"))
                .andExpect(jsonPath("$..version").doesNotExist());
    }

    @Test
    void itNeedsAnAccount() throws Exception {
        mockMvc.perform(get("/api/v1/open-source-components")).andExpect(status().isUnauthorized());
    }
}
