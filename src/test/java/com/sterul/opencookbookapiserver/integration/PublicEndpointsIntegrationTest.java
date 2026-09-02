package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.sterul.opencookbookapiserver.controllers.sharing.SharePaths;

import java.util.List;
import java.util.Set;

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
    // Named, because the actuator contributes a handler mapping of its own.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMappings;

    @ParameterizedTest(name = "GET {0} -> {1}")
    @CsvSource({
            "/api/v1/instance,      200",
            "/actuator/health,      200",
            "/api-docs,             302",
            "/swagger-ui/index.html,200",
            // reaches the controller and fails on the missing request parameter, i.e. not blocked
            "/api/v1/bringexport,   400",
            // reaches the controller and finds no such share, i.e. not blocked
            "/api/v1/shared/no-such-share, 404",
            "/api/v1/shared/no-such-share/images/no-such-image, 404",
    })
    void whitelistedEndpointIsReachableWithoutAuthentication(String path, int expectedStatus) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().is(expectedStatus));
    }

    @Test
    void anInstanceWithSharingOnSaysSo() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingEnabled").value(true));
    }

    @Test
    void adminFrontendIsNotRejectedBySecurity() throws Exception {
        var status = mockMvc.perform(get("/admin")).andReturn().getResponse().getStatus();

        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status, "/admin must stay whitelisted");
        assertNotEquals(HttpStatus.FORBIDDEN.value(), status, "/admin must stay whitelisted");
    }

    @ParameterizedTest(name = "GET {0} -> 401")
    @ValueSource(strings = {
            "/api/v1/recipes",
            "/api/v1/weekplan/2026-01-01/to/2026-01-31",
            "/api/v1/ingredients",
            "/actuator/mappings",
            // Managing shares is not reading them. The whitelist matches on path alone, so this
            // is what stops a wider pattern from publishing revoking and importing as well.
            "/api/v1/shares",
    })
    void everythingElseStillRequiresAuthentication(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} -> 401")
    @CsvSource({
            "POST,   /api/v1/shares",
            "POST,   /api/v1/shares/any-share/import",
            "DELETE, /api/v1/shares/any-share",
    })
    void changingSharesAlwaysRequiresAuthentication(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The whitelist opens a whole path prefix, and it cannot express a method - so the rule that
     * keeps the public part of the API read only is "nothing that writes is mapped under there".
     * Checking the endpoints that exist today would not catch the one somebody adds tomorrow, so
     * this asks the framework what is actually mapped.
     */
    @Test
    void nothingMappedUnderThePublicPrefixCanWrite() {
        var mappingsThatCouldWrite = handlerMappings.getHandlerMethods().keySet().stream()
                .filter(this::isUnderThePublicSharePrefix)
                .filter(mapping -> !readOnly(mapping))
                .map(RequestMappingInfo::toString)
                .toList();

        assertTrue(mappingsThatCouldWrite.isEmpty(),
                "These are reachable without authentication and are not read only: "
                        + mappingsThatCouldWrite);
    }

    @Test
    void thePublicPrefixActuallyHasEndpointsUnderIt() {
        // Without this the test above would pass just as happily if the prefix were renamed and
        // nothing matched it any more.
        var publicMappings = handlerMappings.getHandlerMethods().keySet().stream()
                .filter(this::isUnderThePublicSharePrefix)
                .toList();

        assertEquals(3, publicMappings.size(),
                "Expected the shared recipe and its two image endpoints, found: " + publicMappings);
    }

    private boolean isUnderThePublicSharePrefix(RequestMappingInfo mapping) {
        return patternsOf(mapping).stream().anyMatch(path -> path.startsWith(SharePaths.PUBLIC_BASE));
    }

    /**
     * @param mapping a mapped endpoint
     * @return true when it answers GET and nothing else - an unrestricted mapping answers every
     *         method, so "does not mention POST" is not the same as "cannot write"
     */
    private boolean readOnly(RequestMappingInfo mapping) {
        return Set.of(RequestMethod.GET).equals(mapping.getMethodsCondition().getMethods());
    }

    private List<String> patternsOf(RequestMappingInfo mapping) {
        var patterns = mapping.getPathPatternsCondition();
        return patterns == null ? List.of() : List.copyOf(patterns.getPatternValues());
    }
}
