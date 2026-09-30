package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.account.ApiScope;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.apikeys.ApiKeyService;

/** With keys off, a stored key must sign nobody in, not merely lose its endpoints. */
@SpringBootTest(properties = "opencookbook.api-keys.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ApiKeysDisabledIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "keys-off-anna@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApiKeyService apiKeys;

    @Test
    void aStoredKeyIsRefused() throws Exception {
        var owner = TestAccounts.ensure(userRepository, ANNA);
        var secret = apiKeys.create(owner, "Home Assistant", Set.of(ApiScope.SHOPPING_READ)).secret();

        mockMvc.perform(get("/api/v1/shopping/lists").with(TestAccounts.bearer(secret)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theEndpointsAreGone() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys").with(user(ANNA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(jsonPath("$.apiKeysEnabled").value(false));
    }
}
