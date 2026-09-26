package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

import jakarta.persistence.EntityManagerFactory;

/** A page of a household cookbook costs the same number of queries however many recipes it holds. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class HouseholdCookbookQueryIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "anna@example.invalid";
    private static final String BERT = "bert@example.invalid";
    private static final int FEW_EACH = 1;
    private static final int MORE_EACH = 20;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeImageRepository imageRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String householdId;
    private CookpalUser anna;
    private CookpalUser bert;

    @BeforeEach
    void setup() throws Exception {
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        recipeRepository.deleteAll();

        anna = userNamed(ANNA);
        bert = userNamed(BERT);
        addRecipes(FEW_EACH);
        householdId = householdStartedBy(ANNA);
        join(BERT);
    }

    // Both owners are on both pages, so only the number of recipes differs.
    @Test
    void moreRecipesOnAPageCostNoMoreQueries() throws Exception {
        var few = statementsFor(2 * FEW_EACH);
        addRecipes(MORE_EACH);
        var many = statementsFor(2 * (FEW_EACH + MORE_EACH));

        assertEquals(few, many, "a query per recipe on the page");
    }

    private void addRecipes(int each) {
        for (var index = 0; index < each; index++) {
            recipeWithImage(anna, "Anna " + index);
            recipeWithImage(bert, "Bert " + index);
        }
    }

    private long statementsFor(int recipesOnThePage) throws Exception {
        var statistics = statistics();
        statistics.clear();
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes").with(asUser(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(recipesOnThePage))
                .andExpect(jsonPath("$.recipes[0].titleImageUuid").isNotEmpty())
                .andExpect(jsonPath("$.recipes[0].ownerDisplayName").isNotEmpty());
        return statistics.getPrepareStatementCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private void recipeWithImage(CookpalUser owner, String title) {
        var image = imageRepository.save(RecipeImage.builder().owner(owner).build());
        recipeRepository.save(Recipe.builder()
                .title(title)
                .owner(owner)
                .servings(2)
                .images(new ArrayList<>(List.of(image)))
                .build());
    }

    private String householdStartedBy(String emailAddress) throws Exception {
        var body = mockMvc.perform(post("/api/v1/households")
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Familie Test\",\"shareRecipes\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void join(String emailAddress) throws Exception {
        var invite = mockMvc.perform(post("/api/v1/households/" + householdId + "/invites").with(asUser(ANNA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/household-invites/" + JsonPath.read(invite, "$.token") + "/accept")
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": true}"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor asUser(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name);
    }

    private CookpalUser userNamed(String emailAddress) {
        var existing = userRepository.findByEmailAddress(emailAddress);
        if (existing != null) {
            return existing;
        }
        var user = new CookpalUser();
        user.setEmailAddress(emailAddress);
        user.setPasswordHash("irrelevant");
        user.setActivated(true);
        return userRepository.save(user);
    }
}
