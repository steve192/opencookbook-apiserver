package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * The public share endpoints under load from one client.
 *
 * The budgets are turned down to something a test can reach; what is being checked is that they
 * are applied at all, that a refusal says when to come back, and that they are applied to the
 * public endpoints only.
 */
@SpringBootTest(properties = {
        "opencookbook.sharing.views-per-hour-per-ip=2",
        "opencookbook.sharing.image-views-per-hour-per-ip=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShareRateLimitIntegrationTest extends IntegrationTest {

    private static final String OWNER = "share-ratelimit@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;

    private String shareId;
    private Long recipeId;

    @BeforeEach
    void setup() throws Exception {
        var owner = userRepository.findByEmailAddress(OWNER);
        if (owner == null) {
            owner = new CookpalUser();
            owner.setEmailAddress(OWNER);
            owner.setPasswordHash("irrelevant");
            owner.setActivated(true);
            owner = userRepository.save(owner);
        }
        var recipe = new Recipe();
        recipe.setTitle("Rate limited recipe");
        recipe.setOwner(owner);
        recipeId = recipeRepository.save(recipe).getId();

        var response = mockMvc.perform(post("/api/v1/shares")
                .with(user(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeId\": " + recipeId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        shareId = JsonPath.read(response, "$.shareId");
    }

    @Test
    void readingBeyondTheBudgetIsRefusedWithARetryAfter() throws Exception {
        var client = fromClient("203.0.113.1");

        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void oneClientRunningOutDoesNotShutOutTheRest() throws Exception {
        var exhaustedClient = fromClient("203.0.113.2");
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(exhaustedClient)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(exhaustedClient)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(exhaustedClient))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/shared/" + shareId).with(fromClient("203.0.113.3")))
                .andExpect(status().isOk());
    }

    @Test
    void imagesAreBudgetedSeparatelyFromTheRecipe() throws Exception {
        var client = fromClient("203.0.113.4");

        // One image request is allowed, and it must not have eaten into the recipe budget: a
        // recipe with several pictures would otherwise lock itself out on the first render.
        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/does-not-exist").with(client))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/does-not-exist").with(client))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isOk());
    }

    /**
     * Makes a request look like it came from a given address.
     *
     * Budgets are per address and the limiter is a singleton, so without this every test method
     * would be spending the same budget as the ones before it.
     *
     * @param address the client address to claim
     * @return a post processor stamping that address on the request
     */
    private static RequestPostProcessor fromClient(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @Test
    void theOwnersOwnEndpointsAreNotRateLimited() throws Exception {
        // The budgets guard what anonymous callers can reach. Applying them to the owner's own
        // endpoints as well would throttle somebody using their own cookbook.
        for (var attempt = 0; attempt < 5; attempt++) {
            var status = mockMvc.perform(get("/api/v1/shares")
                    .param("recipeId", recipeId.toString())
                    .with(user(OWNER)))
                    .andReturn().getResponse().getStatus();

            assertEquals(HttpStatus.OK.value(), status);
        }
    }
}
