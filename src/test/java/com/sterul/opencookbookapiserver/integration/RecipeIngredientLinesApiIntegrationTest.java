package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** Reading what a cook typed into an ingredient row, as an import reads its lines. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeIngredientLinesApiIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "ingredient-lines-cook@example.com";
    private static final String PATH = "/api/v1/recipes/ingredient-lines";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cook() {
        TestAccounts.ensure(userRepository, COOK);
    }

    @Test
    void eachLineIsReadInOrder() throws Exception {
        read("{\"lines\": [\"1 1/2 EL Butter, weich\", \"Salz\", \"200 g\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients.length()").value(3))
                .andExpect(jsonPath("$.ingredients[0].amount").value(1.5))
                .andExpect(jsonPath("$.ingredients[0].unit").value("EL"))
                .andExpect(jsonPath("$.ingredients[0].ingredient.name").value("Butter"))
                .andExpect(jsonPath("$.ingredients[0].note").value("weich"))
                .andExpect(jsonPath("$.ingredients[1].amount").doesNotExist())
                .andExpect(jsonPath("$.ingredients[1].ingredient.name").value("Salz"))
                .andExpect(jsonPath("$.ingredients[2]").doesNotExist());
    }

    @Test
    void blankOrOverlongLinesAreRefused() throws Exception {
        read("{\"lines\": [\" \"]}").andExpect(status().isBadRequest());
        read("{\"lines\": [\"" + "a".repeat(501) + "\"]}").andExpect(status().isBadRequest());
        read("{}").andExpect(status().isBadRequest());
    }

    @Test
    void readingNeedsASignIn() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"lines\": [\"Salz\"]}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions read(String body) throws Exception {
        return mockMvc.perform(post(PATH).with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
