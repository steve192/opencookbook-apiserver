package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** An instance as configured by default: nutrition estimation is opt-in while it is new. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class NutritionDisabledIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theInstanceSaysSoUpFrontSoTheAppShowsNoValues() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutritionEnabled").value(false));
    }

    @ParameterizedTest(name = "{0} {1} is gone")
    @CsvSource({
            "GET, /api/v1/recipes/1/nutrition",
            "GET, /api/v1/catalogue/search?q=Zucker",
            "PUT, /api/v1/ingredients/1/link",
            "GET, /api/v1/shared/any-share/nutrition",
    })
    void nutritionEndpointsCannotBeReached(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(user("cook@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCatalogueCannotBeReached() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogue/foods").with(user("admin@example.com").authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
