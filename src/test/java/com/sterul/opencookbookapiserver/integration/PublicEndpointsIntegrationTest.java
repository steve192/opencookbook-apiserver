package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards the {@code AUTH_WHITELIST}. The entries use a "/**" suffix, which also has to match the
 * bare path - {@code /api/v1/instance} is a real endpoint, {@code /api/v1/instance/} is not.
 * Expected statuses are asserted exactly so that a silently introduced 401 cannot pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class PublicEndpointsIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "GET {0} -> {1}")
    @CsvSource({
            "/api/v1/instance,      200",
            "/actuator/health,      200",
            "/admin,                200",
            "/api-docs,             302",
            "/swagger-ui/index.html,200",
            // reaches the controller and fails on the missing request parameter, i.e. not blocked
            "/api/v1/bringexport,   400",
    })
    void whitelistedEndpointIsReachableWithoutAuthentication(String path, int expectedStatus) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest(name = "GET {0} -> 401")
    @ValueSource(strings = {
            "/api/v1/recipes",
            "/api/v1/weekplan/2026-01-01/to/2026-01-31",
            "/api/v1/ingredients",
            "/actuator/mappings",
    })
    void everythingElseStillRequiresAuthentication(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
