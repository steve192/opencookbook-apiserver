package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** Costly endpoints spend a budget per account, wherever the account calls from. */
@SpringBootTest(properties = "opencookbook.rate-limits.expensive.signed-in-per-hour=2")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ExpensiveRateLimitIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "expensive-cook@example.invalid";
    private static final String OTHER_COOK = "expensive-other@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void anAccountRunsOutWhereverItCallsFromAndOthersDoNot() throws Exception {
        TestAccounts.ensure(userRepository, COOK);
        TestAccounts.ensure(userRepository, OTHER_COOK);

        mockMvc.perform(suggest(COOK, "10.0.1.1")).andExpect(status().isOk());
        mockMvc.perform(suggest(COOK, "10.0.1.2")).andExpect(status().isOk());

        mockMvc.perform(suggest(COOK, "10.0.1.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(suggest(OTHER_COOK, "10.0.1.3")).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder suggest(String account, String address) {
        return post("/api/v1/recipes/suggestions").with(user(account)).contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                });
    }
}
