package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Pins the weekplan wire format. The day is a {@code LocalDate}: it has to keep serializing as
 * plain {@code yyyy-MM-dd} and must come back as the very date it was stored under, independent
 * of the JVM timezone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class WeekplanApiIntegrationTest extends IntegrationTest {

    private static final String USER = "weekplan-api@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;

    private Long recipeId;

    @BeforeEach
    void setup() {
        var user = userRepository.findByEmailAddress(USER);
        if (user == null) {
            user = new CookpalUser();
            user.setEmailAddress(USER);
            user.setPasswordHash("irrelevant");
            user.setActivated(true);
            user = userRepository.save(user);
        }
        var recipe = new Recipe();
        recipe.setTitle("Weekplan recipe");
        recipe.setOwner(user);
        recipeId = recipeRepository.save(recipe).getId();
    }

    @Test
    @WithMockUser(username = USER)
    void weekplanDayRoundTripsUnderTheDateItWasStoredWith() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/2026-01-05")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "recipes": [
                            { "type": "NORMAL_RECIPE", "id": %d },
                            { "type": "SIMPLE_RECIPE", "id": "", "title": "Leftovers" }
                        ] }
                        """.formatted(recipeId)))
                .andExpect(status().isOk())
                // the date must not drift by a day through the timezone
                .andExpect(jsonPath("$.day").value("2026-01-05"))
                .andExpect(jsonPath("$.recipes.length()").value(2));

        mockMvc.perform(get("/api/v1/weekplan/2026-01-01/to/2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].day").value("2026-01-05"))
                .andExpect(jsonPath("$[0].recipes[?(@.type=='NORMAL_RECIPE')].title").value("Weekplan recipe"))
                .andExpect(jsonPath("$[0].recipes[?(@.type=='SIMPLE_RECIPE')].title").value("Leftovers"));
    }

    @Test
    @WithMockUser(username = USER)
    void weekplanDayOutsideTheRequestedRangeIsNotReturned() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/2026-03-09")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"recipes\": [ { \"type\": \"SIMPLE_RECIPE\", \"id\": \"\", \"title\": \"Soup\" } ] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value("2026-03-09"));

        mockMvc.perform(get("/api/v1/weekplan/2026-01-01/to/2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.day=='2026-03-09')]").isEmpty());
    }
}
