package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.PlanDraftRepository;
import com.sterul.opencookbookapiserver.repositories.PlanningProfileRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;

/**
 * Generating a week end to end, with nutrition switched off - the planner has to work on every
 * instance, not only on those that estimate calories.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class PlanningApiIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "planning-cook@example.com";
    private static final String STRANGER = "planning-stranger@example.com";
    /** Deletes their account in a test, so nobody else's fixtures go with it. */
    private static final String LEAVER = "planning-leaver@example.com";
    /** A Monday, so the week reads Monday to Sunday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    private static final String PROFILE = """
            { "name": "Normale Woche", "householdSize": 2, "diet": "VEGETARIAN", "cooldownWeeks": 0,
              "leftoversAllowed": false, "spreadVariety": true,
              "meals": [
                { "mealType": "BREAKFAST", "days": {} },
                { "mealType": "DINNER", "days": { "WEDNESDAY": "SIMPLE", "SATURDAY": "ELABORATE", "SUNDAY": "ANY" } } ] }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PlanDraftRepository draftRepository;
    @Autowired
    private PlanningProfileRepository profileRepository;
    @Autowired
    private WeekplanDayRepository weekplanDayRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cookbook() throws Exception {
        draftRepository.deleteAll();
        profileRepository.deleteAll();
        weekplanDayRepository.deleteAll();
        shareRepository.deleteAll();
        recipeRepository.deleteAll();
        TestAccounts.ensure(userRepository, COOK);
        TestAccounts.ensure(userRepository, STRANGER);
        for (var index = 1; index <= 6; index++) {
            recipe("Gemüsegericht " + index, "VEGAN");
        }
        recipe("Gulasch", "MEAT");
    }

    /**
     * Breakfast never cooked, dinner on three chosen days: everything else is left to the cook.
     */
    @Test
    void aWeekIsLaidOutFromTheProfile() throws Exception {
        var draft = generate(createProfile());

        assertThat(JsonPath.<List<String>>read(draft, "$.slots[?(@.mealType == 'BREAKFAST')].kind"))
                .hasSize(7).containsOnly("GAP");
        assertThat(JsonPath.<List<String>>read(draft, "$.slots[?(@.mealType == 'DINNER' && @.kind == 'COOKED')].date"))
                .containsExactly("2026-09-23", "2026-09-26", "2026-09-27");
        assertThat(JsonPath.<List<Object>>read(draft, "$.slots[?(@.kind == 'COOKED')].recipe.id")).doesNotContainNull();
    }

    /** The diet is a hard filter: no amount of scoring puts a meat dish into a vegetarian week. */
    @Test
    void theDietIsNeverBroken() throws Exception {
        var draft = generate(createProfile());

        assertThat(JsonPath.<List<String>>read(draft, "$.slots[?(@.kind == 'COOKED')].recipe.title"))
                .doesNotContain("Gulasch");
    }

    @Test
    void oneMealIsDrawnAgainWithoutTheRecipeItHad() throws Exception {
        var draft = generate(createProfile());
        var draftId = JsonPath.<Integer>read(draft, "$.id");
        var slotId = JsonPath.<List<Integer>>read(draft, "$.slots[?(@.kind == 'COOKED')].id").get(0);
        var before = JsonPath.<List<Integer>>read(draft, "$.slots[?(@.id == " + slotId + ")].recipe.id").get(0);

        var after = mockMvc.perform(post("/api/v1/planning/drafts/" + draftId + "/slots/" + slotId + "/reroll")
                        .with(user(COOK)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Integer>>read(after, "$.slots[?(@.id == " + slotId + ")].recipe.id").get(0))
                .isNotEqualTo(before);
    }

    /** "This Friday is takeaway" is one tap, and so is the opposite. */
    @Test
    void aGapCanBecomeAMealAndBackAgain() throws Exception {
        var draft = generate(createProfile());
        var draftId = JsonPath.<Integer>read(draft, "$.id");
        var fridayDinner = JsonPath.<List<Integer>>read(draft,
                "$.slots[?(@.date == '2026-09-25' && @.mealType == 'DINNER')].id").get(0);

        var cooked = toggle(draftId, fridayDinner);
        assertThat(JsonPath.<List<String>>read(cooked, "$.slots[?(@.id == " + fridayDinner + ")].kind")).containsExactly("COOKED");
        assertThat(JsonPath.<List<Object>>read(cooked, "$.slots[?(@.id == " + fridayDinner + ")].recipe.id")).doesNotContainNull();

        var gap = toggle(draftId, fridayDinner);
        assertThat(JsonPath.<List<String>>read(gap, "$.slots[?(@.id == " + fridayDinner + ")].kind")).containsExactly("GAP");
    }

    /** Accepting adds to each day and never removes what the cook planned by hand; gaps are not written. */
    @Test
    void acceptingAddsTheWeekToTheWeekplanAndKeepsWhatWasThere() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/2026-09-26").with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipes\": [{\"type\": \"SIMPLE_RECIPE\", \"title\": \"Brunch bei Oma\"}]}"))
                .andExpect(status().isOk());
        var draftId = JsonPath.<Integer>read(generate(createProfile()), "$.id");

        mockMvc.perform(post("/api/v1/planning/drafts/" + draftId + "/accept").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        transactionTemplate.executeWithoutResult(status -> {
            var saturday = weekplanDayRepository.findSingleDay(MONDAY.plusDays(5), PlanScope.of(cook()));
            assertThat(saturday.getRecipes()).hasSize(2);
            assertThat(saturday.getRecipes().get(0).getSimpleRecipeText()).isEqualTo("Brunch bei Oma");
            assertThat(saturday.getRecipes().get(1).getRecipe()).isNotNull();
            assertThat(weekplanDayRepository.findSingleDay(MONDAY, PlanScope.of(cook()))).isNull();
        });
    }

    @Test
    void anAcceptedDraftCannotBeChangedAnyMore() throws Exception {
        var draftId = JsonPath.<Integer>read(generate(createProfile()), "$.id");
        mockMvc.perform(post("/api/v1/planning/drafts/" + draftId + "/accept").with(user(COOK))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/planning/drafts/" + draftId + "/reroll").with(user(COOK)))
                .andExpect(status().isNotFound());
    }

    @Test
    void somebodyElsesDraftAndProfileAreNotFound() throws Exception {
        var profileId = createProfile();
        var draftId = JsonPath.<Integer>read(generate(profileId), "$.id");

        mockMvc.perform(get("/api/v1/planning/drafts/" + draftId).with(user(STRANGER))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/planning/drafts/" + draftId).with(user(STRANGER))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/planning/profiles/" + profileId).with(user(STRANGER))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/planning/drafts").with(user(STRANGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\": " + profileId + ", \"startDate\": \"2026-09-21\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theFirstProfileIsTheDefault() throws Exception {
        createProfile();

        mockMvc.perform(get("/api/v1/planning/profiles").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].defaultProfile").value(true))
                .andExpect(jsonPath("$[0].meals.length()").value(2));
    }

    /** The wizard opens a profile as it was saved: the chosen days and each day's effort come back. */
    @Test
    void daysAndEffortsAreKept() throws Exception {
        createProfile();

        mockMvc.perform(get("/api/v1/planning/profiles").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meals[?(@.mealType == 'DINNER')].days.WEDNESDAY").value("SIMPLE"))
                .andExpect(jsonPath("$[0].meals[?(@.mealType == 'DINNER')].days.SATURDAY").value("ELABORATE"))
                .andExpect(jsonPath("$[0].meals[?(@.mealType == 'DINNER')].days.MONDAY").doesNotExist());
    }

    @Test
    void aProfileWithoutMealsIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/planning/profiles").with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Leer\", \"householdSize\": 2, \"meals\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRerollTakesAReasonForPassingTheRecipeOver() throws Exception {
        var draft = generate(createProfile());
        var draftId = JsonPath.<Integer>read(draft, "$.id");
        var slotId = JsonPath.<List<Integer>>read(draft, "$.slots[?(@.kind == 'COOKED')].id").get(0);
        var before = JsonPath.<List<Integer>>read(draft, "$.slots[?(@.id == " + slotId + ")].recipe.id").get(0);

        var after = reroll(draftId, slotId, "{\"reason\": \"TOO_MUCH_WORK\"}");

        assertThat(JsonPath.<List<Integer>>read(after, "$.slots[?(@.id == " + slotId + ")].recipe.id").get(0))
                .isNotEqualTo(before);
    }

    /** Deleting an account takes its profiles and drafts with it, and nobody else's. */
    @Test
    void deletingTheAccountTakesItsPlanningWithIt() throws Exception {
        generate(createProfile());
        var draftsOfOthers = draftRepository.count();
        var profilesOfOthers = profileRepository.count();
        TestAccounts.ensure(userRepository, LEAVER);
        recipe(LEAVER, "Suppe", "VEGAN");
        generate(createProfile(LEAVER), LEAVER);

        mockMvc.perform(delete("/api/v1/users/self").with(user(LEAVER))).andExpect(status().isOk());

        assertThat(draftRepository.count()).isEqualTo(draftsOfOthers);
        assertThat(profileRepository.count()).isEqualTo(profilesOfOthers);
    }

    private String reroll(int draftId, int slotId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/planning/drafts/" + draftId + "/slots/" + slotId + "/reroll")
                        .with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int createProfile() throws Exception {
        return createProfile(COOK);
    }

    private int createProfile(String cook) throws Exception {
        var created = mockMvc.perform(post("/api/v1/planning/profiles").with(user(cook))
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private String generate(int profileId) throws Exception {
        return generate(profileId, COOK);
    }

    private String generate(int profileId, String cook) throws Exception {
        return mockMvc.perform(post("/api/v1/planning/drafts").with(user(cook)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\": " + profileId + ", \"startDate\": \"" + MONDAY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String toggle(int draftId, int slotId) throws Exception {
        return mockMvc.perform(post("/api/v1/planning/drafts/" + draftId + "/slots/" + slotId + "/toggle-gap")
                        .with(user(COOK)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void recipe(String title, String diet) throws Exception {
        recipe(COOK, title, diet);
    }

    private void recipe(String cook, String title, String diet) throws Exception {
        mockMvc.perform(post("/api/v1/recipes").with(user(cook)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\", \"servings\": 2, \"totalTime\": 25, \"recipeType\": \""
                                + diet + "\", \"neededIngredients\": []}"))
                .andExpect(status().isOk());
    }

    private CookpalUser cook() {
        return userRepository.findByEmailAddress(COOK);
    }
}
