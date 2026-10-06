package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Whose recipes "what to cook" draws on: your own always, and those shared with your households only
 * when you ask for them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeSuggestionApiIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "suggest-anna@example.invalid";
    private static final String BERT = "suggest-bert@example.invalid";
    private static final String CARLA = "suggest-carla@example.invalid";
    private static final String DORA = "suggest-dora@example.invalid";
    private static final String EVE = "suggest-eve@example.invalid";

    /** What the app sends until the household switch is touched, and always without a household. */
    private static final String UNANSWERED = "{}";
    private static final String OWN_ONLY = "{\"includeHouseholdRecipes\": false}";
    private static final String WITH_HOUSEHOLDS = "{\"includeHouseholdRecipes\": true}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;

    @BeforeEach
    void setup() {
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        recipeRepository.deleteAll();
        for (var cook : List.of(ANNA, BERT, CARLA, DORA, EVE)) {
            recipeOf(cook);
        }
    }

    @Test
    void withoutAHouseholdYourOwnRecipesAreSuggested() throws Exception {
        assertThat(suggestedFor(ANNA, UNANSWERED)).containsExactly(recipeTitle(ANNA));
        assertThat(suggestedFor(ANNA, OWN_ONLY)).containsExactly(recipeTitle(ANNA));
        assertThat(suggestedFor(ANNA, WITH_HOUSEHOLDS)).containsExactly(recipeTitle(ANNA));
    }

    @Test
    void inAHouseholdOnlyYourOwnRecipesAreSuggestedUnlessYouAsk() throws Exception {
        var household = TestHouseholds.start(mockMvc, ANNA, "Familie");
        TestHouseholds.join(mockMvc, household, ANNA, BERT);
        TestHouseholds.share(mockMvc, household, BERT);

        for (var cook : List.of(ANNA, BERT)) {
            assertThat(suggestedFor(cook, UNANSWERED)).containsExactly(recipeTitle(cook));
            assertThat(suggestedFor(cook, OWN_ONLY)).containsExactly(recipeTitle(cook));
            assertThat(suggestedFor(cook, WITH_HOUSEHOLDS))
                    .containsExactlyInAnyOrder(recipeTitle(ANNA), recipeTitle(BERT));
        }
    }

    @Test
    void yourOwnRecipesAreSuggestedThoughYouDoNotShareThem() throws Exception {
        var household = TestHouseholds.start(mockMvc, ANNA, "Familie");
        TestHouseholds.join(mockMvc, household, ANNA, DORA);

        assertThat(suggestedFor(DORA, OWN_ONLY)).containsExactly(recipeTitle(DORA));
        assertThat(suggestedFor(DORA, WITH_HOUSEHOLDS)).containsExactlyInAnyOrder(recipeTitle(ANNA), recipeTitle(DORA));
        // Dora keeps hers to herself, so Anna is offered only her own.
        assertThat(suggestedFor(ANNA, WITH_HOUSEHOLDS)).containsExactly(recipeTitle(ANNA));
    }

    @Test
    void inSeveralHouseholdsEveryCookbookSharedWithYouIsAddedOnlyWhenYouAsk() throws Exception {
        var family = TestHouseholds.start(mockMvc, ANNA, "Familie");
        TestHouseholds.join(mockMvc, family, ANNA, BERT);
        TestHouseholds.share(mockMvc, family, BERT);
        var flatshare = TestHouseholds.start(mockMvc, ANNA, "WG");
        TestHouseholds.join(mockMvc, flatshare, ANNA, CARLA);
        TestHouseholds.share(mockMvc, flatshare, CARLA);
        TestHouseholds.join(mockMvc, flatshare, ANNA, DORA);

        assertThat(suggestedFor(ANNA, UNANSWERED)).containsExactly(recipeTitle(ANNA));
        assertThat(suggestedFor(ANNA, OWN_ONLY)).containsExactly(recipeTitle(ANNA));
        assertThat(suggestedFor(ANNA, WITH_HOUSEHOLDS))
                .containsExactlyInAnyOrder(recipeTitle(ANNA), recipeTitle(BERT), recipeTitle(CARLA));
        // Bert and Carla share one household each with Anna, and none with each other.
        assertThat(suggestedFor(BERT, WITH_HOUSEHOLDS)).containsExactlyInAnyOrder(recipeTitle(ANNA), recipeTitle(BERT));
        assertThat(suggestedFor(CARLA, UNANSWERED)).containsExactly(recipeTitle(CARLA));
        assertThat(suggestedFor(CARLA, WITH_HOUSEHOLDS)).containsExactlyInAnyOrder(recipeTitle(ANNA), recipeTitle(CARLA));
        assertThat(suggestedFor(EVE, WITH_HOUSEHOLDS)).containsExactly(recipeTitle(EVE));
    }

    private List<String> suggestedFor(String cook, String request) throws Exception {
        var body = mockMvc.perform(post("/api/v1/recipes/suggestions").with(user(cook))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.results[*].recipe.title");
    }

    private void recipeOf(String cook) {
        recipeRepository.save(Recipe.builder()
                .title(recipeTitle(cook)).owner(TestAccounts.ensure(userRepository, cook)).servings(2)
                .images(new ArrayList<>()).preparationSteps(new ArrayList<>())
                .neededIngredients(new ArrayList<>()).recipeGroups(new ArrayList<>())
                .build());
    }

    private static String recipeTitle(String cook) {
        return "Gericht von " + cook;
    }
}
