package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * One access rule, three ways in: the listing, opening by id and the nutrition sheets agree for every
 * reader. The app reads only the listing, so this is what keeps it from showing more or less than
 * the server would open.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeAccessContractIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "anna@example.invalid";
    private static final String BERT = "bert@example.invalid";
    private static final String CARLA = "carla@example.invalid";
    private static final String DORA = "dora@example.invalid";
    private static final String ERIK = "erik@example.invalid";
    private static final String STRANGER = "stranger@example.invalid";
    private static final List<String> EVERYBODY = List.of(ANNA, BERT, CARLA, DORA, ERIK, STRANGER);

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

    private final Map<String, Long> recipeOf = new HashMap<>();
    private String home;
    private String club;

    /**
     * Home: Anna and Bert share, Carla joined without sharing, Dora shared and left. Club: Bert and
     * Erik share. The stranger is in no household.
     */
    @BeforeEach
    void setup() throws Exception {
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        recipeRepository.deleteAll();

        for (var name : EVERYBODY) {
            var owner = TestAccounts.ensure(userRepository, name);
            recipeOf.put(name, recipeRepository.save(Recipe.builder()
                    .title("Recipe of " + name).owner(owner).servings(2).images(new ArrayList<>()).build()).getId());
        }

        home = TestHouseholds.start(mockMvc, ANNA, "Home");
        TestHouseholds.join(mockMvc, home, ANNA, BERT);
        TestHouseholds.share(mockMvc, home, BERT);
        TestHouseholds.join(mockMvc, home, ANNA, CARLA);
        TestHouseholds.join(mockMvc, home, ANNA, DORA);
        TestHouseholds.share(mockMvc, home, DORA);
        leave(home, DORA);

        club = TestHouseholds.start(mockMvc, BERT, "Club");
        TestHouseholds.join(mockMvc, club, BERT, ERIK);
        TestHouseholds.share(mockMvc, club, ERIK);
    }

    @Test
    void theListingOpeningByIdAndNutritionAgreeForEveryReader() throws Exception {
        for (var reader : EVERYBODY) {
            var listed = ids(reader, "/api/v1/recipes", "$[*].id");
            var withNutrition = ids(reader, "/api/v1/recipes/nutrition", "$[*].recipeId");
            for (var owner : EVERYBODY) {
                var recipeId = recipeOf.get(owner);
                var opens = mockMvc.perform(get("/api/v1/recipes/" + recipeId).with(user(reader)))
                        .andReturn().getResponse().getStatus() == 200;

                assertEquals(opens, listed.contains(recipeId), reader + " listing the recipe of " + owner);
                assertEquals(opens, withNutrition.contains(recipeId), reader + " reading nutrition of " + owner);
            }
        }
    }

    @Test
    void everybodyReadsWhatTheirHouseholdsShow() throws Exception {
        assertEquals(Set.of(ANNA, BERT), ownersListedFor(ANNA));
        assertEquals(Set.of(ANNA, BERT, ERIK), ownersListedFor(BERT));
        assertEquals(Set.of(ANNA, BERT, CARLA), ownersListedFor(CARLA));
        assertEquals(Set.of(DORA), ownersListedFor(DORA));
        assertEquals(Set.of(BERT, ERIK), ownersListedFor(ERIK));
        assertEquals(Set.of(STRANGER), ownersListedFor(STRANGER));
    }

    @Test
    void eachRecipeNamesTheReadersHouseholdsShowingIt() throws Exception {
        assertEquals(Set.of(home, club), householdsShowing(BERT, BERT));
        assertEquals(Set.of(home), householdsShowing(BERT, ANNA));
        assertEquals(Set.of(club), householdsShowing(BERT, ERIK));
        assertEquals(Set.of(club), householdsShowing(ERIK, BERT));
        assertEquals(Set.of(), householdsShowing(CARLA, CARLA));
        assertEquals(Set.of(), householdsShowing(DORA, DORA));
    }

    private Set<Long> ids(String reader, String path, String idPath) throws Exception {
        List<Number> ids = JsonPath.read(body(reader, path), idPath);
        return ids.stream().map(Number::longValue).collect(Collectors.toSet());
    }

    private Set<String> ownersListedFor(String reader) throws Exception {
        var listed = ids(reader, "/api/v1/recipes", "$[*].id");
        return EVERYBODY.stream().filter(owner -> listed.contains(recipeOf.get(owner))).collect(Collectors.toSet());
    }

    private Set<String> householdsShowing(String reader, String owner) throws Exception {
        List<List<String>> found = JsonPath.read(body(reader, "/api/v1/recipes"),
                "$[?(@.id == " + recipeOf.get(owner) + ")].householdIds");
        return new HashSet<>(found.get(0));
    }

    private String body(String reader, String path) throws Exception {
        return mockMvc.perform(get(path).with(user(reader)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void leave(String householdId, String member) throws Exception {
        var memberId = userRepository.findByEmailAddress(member).getUserId();
        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + memberId).with(user(member)))
                .andExpect(status().isNoContent());
    }
}
