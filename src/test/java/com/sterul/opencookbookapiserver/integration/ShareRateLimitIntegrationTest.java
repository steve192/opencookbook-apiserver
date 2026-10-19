package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * The public share endpoints under load. A share link is meant for a few people, so everything behind it spends one
 * budget per link, whoever opens it and from wherever.
 *
 * The budget is turned down to something a test can reach. Each test makes its own share, as the budget lives as
 * long as the context.
 */
@SpringBootTest(properties = "opencookbook.rate-limits.share-link-per-day=3")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShareRateLimitIntegrationTest extends IntegrationTestBase {

    private static final String OWNER = "share-ratelimit@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;

    private CookpalUser owner;
    private Long recipeId;
    private String shareId;

    @BeforeEach
    void setup() throws Exception {
        owner = userRepository.findByEmailAddress(OWNER);
        if (owner == null) {
            owner = new CookpalUser();
            owner.setEmailAddress(OWNER);
            owner.setPasswordHash("irrelevant");
            owner.setActivated(true);
            owner = userRepository.save(owner);
        }
        recipeId = newRecipe();
        shareId = share(recipeId);
    }

    @Test
    void aLinkOpenedBeyondItsBudgetIsRefusedFromAnywhereWithARetryAfter() throws Exception {
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(fromClient("203.0.113.1"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(fromClient("203.0.113.2"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(user("reader@example.com"))).andExpect(status().isOk());

        // The interceptor throws rather than writing a body of its own, so a refusal here reads
        // exactly like a refusal from anywhere else.
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(fromClient("203.0.113.3")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void oneLinkRunningOutLeavesTheOthersAlone() throws Exception {
        var client = fromClient("203.0.113.4");
        for (var request = 0; request < 3; request++) {
            mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/shared/" + share(newRecipe())).with(client)).andExpect(status().isOk());
    }

    @Test
    void imagesAndNutritionSpendTheLinksBudget() throws Exception {
        var client = fromClient("203.0.113.5");

        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/does-not-exist").with(client))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/shared/" + shareId + "/nutrition").with(client)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shared/" + shareId).with(client)).andExpect(status().isTooManyRequests());
    }

    @Test
    void theOwnersOwnEndpointsAreNotRateLimited() throws Exception {
        // The budget guards the link. Applying it to the owner's own endpoints as well would
        // throttle somebody using their own cookbook.
        for (var attempt = 0; attempt < 5; attempt++) {
            var status = mockMvc.perform(get("/api/v1/shares")
                    .param("recipeId", recipeId.toString())
                    .with(user(OWNER)))
                    .andReturn().getResponse().getStatus();

            assertEquals(HttpStatus.OK.value(), status);
        }
    }

    private Long newRecipe() {
        var recipe = new Recipe();
        recipe.setTitle("Rate limited recipe");
        recipe.setOwner(owner);
        return recipeRepository.save(recipe).getId();
    }

    private String share(Long recipe) throws Exception {
        var response = mockMvc.perform(post("/api/v1/shares")
                .with(user(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeId\": " + recipe + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.shareId");
    }

    private static RequestPostProcessor fromClient(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
