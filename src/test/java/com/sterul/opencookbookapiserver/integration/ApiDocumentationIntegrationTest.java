package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

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

    /**
     * Every endpoint carries a summary, so the document stays something a reader can use rather
     * than a list of paths. Asked of the generated document rather than of the annotations, so an
     * endpoint added without one fails here instead of quietly shipping blank.
     */
    @Test
    void everyEndpointSaysWhatItDoes() throws Exception {
        var document = mockMvc.perform(get("/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var paths = (Map<String, Map<String, Object>>) JsonPath.read(document, "$.paths");
        var undocumented = paths.entrySet().stream()
                .flatMap(path -> path.getValue().entrySet().stream()
                        .filter(operation -> operation.getValue() instanceof Map<?, ?> details
                                && isBlank(details.get("summary")))
                        .map(operation -> operation.getKey().toUpperCase() + " " + path.getKey()))
                .toList();

        assertTrue(undocumented.isEmpty(), "These endpoints carry no summary: " + undocumented);
    }

    @Test
    void theHouseholdEndpointsAreInTheDocument() throws Exception {
        // Without this the test above would pass just as happily if the household controllers
        // stopped being registered at all.
        mockMvc.perform(get("/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/households']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/household-invites/{token}/accept']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/households/{householdId}/recipes']").exists());
    }

    private static boolean isBlank(Object summary) {
        return !(summary instanceof String text) || text.isBlank();
    }

    @Test
    void swaggerUiConfigurationIsServed() throws Exception {
        // the URL swagger-initializer.js bootstraps from
        mockMvc.perform(get("/api-docs/swagger-config/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("configUrl")));
    }
}
