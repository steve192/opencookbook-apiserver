package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API documentation is served from a customised path ({@code springdoc.swagger-ui.path} and
 * {@code springdoc.api-docs.path}), so the whole chain has to stay reachable without a login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ApiDocumentationIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiEntryPointRedirectsToTheUi() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }

    @Test
    void swaggerUiPageIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiAssetsAreServed() throws Exception {
        for (var asset : new String[] { "swagger-initializer.js", "swagger-ui-bundle.js", "swagger-ui.css" }) {
            mockMvc.perform(get("/swagger-ui/" + asset))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mockMvc.perform(get("/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/recipes']").exists());
    }

    @Test
    void swaggerUiConfigurationIsServed() throws Exception {
        // the URL swagger-initializer.js bootstraps from
        mockMvc.perform(get("/api-docs/swagger-config/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("configUrl")));
    }
}
