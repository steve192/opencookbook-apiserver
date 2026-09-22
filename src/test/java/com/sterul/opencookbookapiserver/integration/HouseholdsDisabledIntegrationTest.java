package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;
import com.sterul.opencookbookapiserver.services.access.CookbookAccess;
import com.sterul.opencookbookapiserver.services.access.PersonalCookbookAccess;

/** With households off, a stored membership must grant nothing, not merely lose its endpoints. */
@SpringBootTest(properties = "opencookbook.households.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class HouseholdsDisabledIntegrationTest extends IntegrationTest {

    private static final String ANNA = "disabled-anna@example.invalid";
    private static final String BERT = "disabled-bert@example.invalid";
    private static final String DAY = "2026-10-05";

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
    @Autowired
    private CookbookAccess cookbookAccess;
    @Autowired
    private WeekplanDayRepository weekplanDayRepository;

    private Long annasRecipeId;
    private String householdId;

    @BeforeEach
    void setup() {
        weekplanDayRepository.deleteAll();
        membershipRepository.deleteAll();
        householdRepository.deleteAll();

        var anna = userNamed(ANNA);
        var bert = userNamed(BERT);
        annasRecipeId = recipeRepository.save(Recipe.builder()
                .title("Annas Lasagne").owner(anna).servings(2)
                .images(new ArrayList<>()).preparationSteps(new ArrayList<>())
                .neededIngredients(new ArrayList<>()).recipeGroups(new ArrayList<>())
                .build()).getId();

        // Both are sharing members of one household - and none of it counts while the feature is off.
        var household = householdRepository.save(Household.builder().name("Familie Test").build());
        householdId = household.getId();
        membershipRepository.save(HouseholdMembership.builder()
                .household(household).member(anna).shareRecipes(true).build());
        membershipRepository.save(HouseholdMembership.builder()
                .household(household).member(bert).shareRecipes(true).build());
    }

    @Test
    void theRuleItselfRevertsToThePersonalOne() {
        assertInstanceOf(PersonalCookbookAccess.class, cookbookAccess,
                "With households off, nothing but the personal rule may be wired in");

        var bert = userRepository.findByEmailAddress(BERT);
        assertEquals(List.of(bert.getUserId()), List.copyOf(cookbookAccess.visibleOwnerIds(bert)),
                "A membership that is still stored must grant nothing");
    }

    @Test
    @WithMockUser(username = BERT)
    void aStoredMembershipNoLongerOpensSomebodyElsesRecipe() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/" + annasRecipeId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = BERT)
    void aStoredMembershipOpensNoHouseholdPlan() throws Exception {
        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY).param("household", householdId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/planning/profiles").param("household", householdId))
                .andExpect(status().isNotFound());
    }

    /**
     * Planned while households were on, and no cleanup ran when they were switched off. The row is
     * still stored; the response must not show it.
     */
    @Test
    @WithMockUser(username = BERT)
    void aMealThatIsNoLongerReadableIsNotShownInTheOwnWeek() throws Exception {
        var day = new WeekplanDay();
        day.setOwner(userRepository.findByEmailAddress(BERT));
        day.setPlanDate(LocalDate.parse(DAY));
        day.setRecipes(new ArrayList<>(List.of(WeekplanDayRecipe.builder()
                .isSimpleRecipe(false).recipe(recipeRepository.findById(annasRecipeId).orElseThrow()).build())));
        weekplanDayRepository.save(day);

        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipes.length()").value(0));
    }

    @ParameterizedTest(name = "{0} {1} is gone")
    @CsvSource({
            "GET,    /api/v1/households",
            "POST,   /api/v1/households",
            "GET,    /api/v1/households/any-household",
            "GET,    /api/v1/households/any-household/recipes",
            "PUT,    /api/v1/households/any-household/sharing",
            "POST,   /api/v1/households/any-household/invites",
            "GET,    /api/v1/household-invites/any-token",
            "POST,   /api/v1/household-invites/any-token/accept",
    })
    @WithMockUser(username = BERT)
    void noHouseholdEndpointIsReachable(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = BERT)
    void administrationStillSeesWhatExists() throws Exception {
        // Deliberately not conditional: an operator who has just switched the feature off still
        // has to be able to look at, and dissolve, what is already there.
        mockMvc.perform(get("/api/v1/admin/households"))
                .andExpect(status().isForbidden());
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
