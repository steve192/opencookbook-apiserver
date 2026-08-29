package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Covers the JSON wire format itself. The other recipe tests call the controller with
 * ready-made Java objects and therefore cannot catch changes in Jackson's binding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeJsonBindingIntegrationTest extends IntegrationTest {

    private static final String USER = "json-binding@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void createUser() {
        if (userRepository.findByEmailAddress(USER) == null) {
            var user = new CookpalUser();
            user.setEmailAddress(USER);
            user.setPasswordHash("irrelevant");
            user.setActivated(true);
            userRepository.save(user);
        }
    }

    @Test
    @WithMockUser(username = USER)
    void recipeWithNestedIngredientIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "Wire format check",
                          "servings": 4,
                          "preparationSteps": ["Step one", "Step two"],
                          "neededIngredients": [
                            { "amount": 2.0, "unit": "g", "ingredient": { "name": "Salz" } }
                          ],
                          "recipeGroups": [ { "title": "Dinner" } ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Wire format check"))
                .andExpect(jsonPath("$.servings").value(4))
                .andExpect(jsonPath("$.preparationSteps.length()").value(2))
                .andExpect(jsonPath("$.recipeGroups[0].title").value("Dinner"))
                .andExpect(jsonPath("$.neededIngredients[0].unit").value("g"))
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.name").value("Salz"))
                // the boolean accessor Jackson derives from `isPublicIngredient`
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.publicIngredient").value(false));
    }

    @Test
    @WithMockUser(username = USER)
    void recipeWithoutOptionalCollectionsIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Minimal", "servings": 2 }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Minimal"));
    }
}
