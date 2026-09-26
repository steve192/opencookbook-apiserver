package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.PlanDraftRepository;
import com.sterul.opencookbookapiserver.repositories.PlanningProfileRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;

/**
 * The shared week: reading merges every plan, writing goes to one, and meals leave a plan once its
 * readers lose access - through sharing switched off, leaving, or being removed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class HouseholdWeekplanApiIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "wp-anna@example.invalid";
    private static final String BERT = "wp-bert@example.invalid";
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
    private WeekplanDayRepository weekplanDayRepository;
    @Autowired
    private PlanDraftRepository draftRepository;
    @Autowired
    private PlanningProfileRepository profileRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long annasRecipeId;
    private String householdId;

    @BeforeEach
    void setup() throws Exception {
        draftRepository.deleteAll();
        profileRepository.deleteAll();
        weekplanDayRepository.deleteAll();
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        recipeRepository.deleteAll();

        var anna = userNamed(ANNA);
        userNamed(BERT);
        annasRecipeId = recipeRepository.save(Recipe.builder()
                .title("Annas Lasagne").owner(anna).servings(2)
                .images(new ArrayList<>()).preparationSteps(new ArrayList<>())
                .neededIngredients(new ArrayList<>()).recipeGroups(new ArrayList<>())
                .build()).getId();

        householdId = householdStartedBy(ANNA);
        join(householdId, BERT);
        planIntoHousehold();
    }

    @Test
    void theWeekIsReadMergedAndSaysWhichPlanEachDayIsFrom() throws Exception {
        planButterbrotForBert();

        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY).param("allPlans", "true").with(asUser(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // His own carries no household; the shared one names the household it is from.
                .andExpect(jsonPath("$[?(@.householdId == null)].recipes[0].title")
                        .value("Butterbrot"))
                .andExpect(jsonPath("$[?(@.householdId != null)].householdName")
                        .value("Familie Test"));
    }

    @Test
    void withoutAskingForAllPlansTheWeekIsYourOwnAsBefore() throws Exception {
        planButterbrotForBert();

        // What an app that predates households asks, and it cannot tell plans apart.
        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY).with(asUser(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].householdId").doesNotExist())
                .andExpect(jsonPath("$[0].recipes[0].title").value("Butterbrot"));
    }

    private void planButterbrotForBert() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/" + DAY)
                        .with(asUser(BERT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipes\":[{\"type\":\"SIMPLE_RECIPE\",\"title\":\"Butterbrot\"}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void oneHouseholdsWeekIsReadableOnItsOwn() throws Exception {
        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY)
                        .param("household", householdId)
                        .with(asUser(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipes[0].title").value("Annas Lasagne"));
    }

    @Test
    void somebodyOutsideCannotReadOrWriteTheHouseholdWeek() throws Exception {
        var stranger = "wp-stranger@example.invalid";
        userNamed(stranger);

        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY)
                        .param("household", householdId)
                        .with(asUser(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/weekplan/" + DAY)
                        .param("household", householdId)
                        .with(asUser(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipes\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void switchingSharingOffTakesTheMealsOutOfTheWeek() throws Exception {
        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing")
                        .with(asUser(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());

        assertHouseholdWeekIsEmpty();
    }

    @Test
    void leavingTakesTheMealsOutOfTheWeek() throws Exception {
        var annaId = userRepository.findByEmailAddress(ANNA).getUserId();

        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + annaId)
                        .with(asUser(ANNA)))
                .andExpect(status().isNoContent());

        assertHouseholdWeekIsEmpty();
    }

    @Test
    void beingRemovedTakesTheMealsOutOfTheWeek() throws Exception {
        var annaId = userRepository.findByEmailAddress(ANNA).getUserId();

        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + annaId)
                        .with(asUser(BERT)))
                .andExpect(status().isNoContent());

        assertHouseholdWeekIsEmpty();
    }

    /** Bert shares nothing, so leaving first keeps Anna's meal on the week the household ends with. */
    @Test
    void aHouseholdEndsWithItsWeekStillPlanned() throws Exception {
        var annaId = userRepository.findByEmailAddress(ANNA).getUserId();
        var bertId = userRepository.findByEmailAddress(BERT).getUserId();

        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + bertId)
                        .with(asUser(BERT)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + annaId)
                        .with(asUser(ANNA)))
                .andExpect(status().isNoContent());

        assertThat(householdRepository.findById(householdId)).isEmpty();
    }

    @Test
    void anAdministratorDissolvesAHouseholdWithItsPlansStillOpen() throws Exception {
        generateForHousehold(householdProfile(List.of()));

        mockMvc.perform(delete("/api/v1/admin/households/" + householdId)
                        .with(SecurityMockMvcRequestPostProcessors.user("wp-admin@example.invalid")
                                .authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isNoContent());

        assertThat(householdRepository.findById(householdId)).isEmpty();
    }

    /** Bert shares nothing, so his own recipe is not in the household and must not reach its week. */
    @Test
    void aRecipeTheHouseholdCannotReadCannotBePlannedIntoIt() throws Exception {
        var bertsRecipe = recipeOf(BERT, "Berts Suppe");
        var meal = "{\"recipes\":[{\"type\":\"NORMAL_RECIPE\",\"id\":" + bertsRecipe + "}]}";

        mockMvc.perform(put("/api/v1/weekplan/" + DAY)
                        .param("household", householdId)
                        .with(asUser(BERT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meal))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/weekplan/" + DAY)
                        .with(asUser(BERT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meal))
                .andExpect(status().isOk());
    }

    /** A personal week is no loophole: what Bert may no longer read leaves his own plan too. */
    @Test
    void switchingSharingOffTakesTheMealsOutOfTheOthersOwnWeeks() throws Exception {
        planIntoOwnWeek(BERT, annasRecipeId);

        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing")
                        .with(asUser(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());

        assertOwnWeekIsEmpty(BERT);
    }

    @Test
    void leavingTakesWhatIsNoLongerReadableOutOfTheLeaversOwnWeek() throws Exception {
        planIntoOwnWeek(BERT, annasRecipeId);
        var bertId = userRepository.findByEmailAddress(BERT).getUserId();

        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + bertId)
                        .with(asUser(BERT)))
                .andExpect(status().isNoContent());

        assertOwnWeekIsEmpty(BERT);
    }

    /** An accepted draft would otherwise write the recipes straight back onto the shared week. */
    @Test
    void switchingSharingOffDrawsTheMealsOfAnOpenDraftAgain() throws Exception {
        var draft = generateForHousehold(householdProfile(List.of()));
        assertThat(cookedRecipeIds(draft)).contains(annasRecipeId.intValue());

        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing")
                        .with(asUser(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());

        var reloaded = mockMvc.perform(get("/api/v1/planning/drafts/" + JsonPath.read(draft, "$.id"))
                        .param("household", householdId)
                        .with(asUser(BERT)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(cookedRecipeIds(reloaded)).doesNotContain(annasRecipeId.intValue());
    }

    @Test
    void aPersonalWeekDrawsOnHouseholdsOnlyWhenItsProfileSaysSo() throws Exception {
        assertThat(cookedRecipeIds(generateOwn(BERT, ownProfile(BERT, false))))
                .doesNotContain(annasRecipeId.intValue());
        assertThat(cookedRecipeIds(generateOwn(BERT, ownProfile(BERT, true))))
                .contains(annasRecipeId.intValue());
    }

    @Test
    void switchingSharingOffDrawsTheMealsOfTheOthersOwnDraftsAgain() throws Exception {
        var draft = generateOwn(BERT, ownProfile(BERT, true));
        assertThat(cookedRecipeIds(draft)).contains(annasRecipeId.intValue());

        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing")
                        .with(asUser(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());

        var reloaded = mockMvc.perform(get("/api/v1/planning/drafts/" + JsonPath.read(draft, "$.id"))
                        .with(asUser(BERT)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(cookedRecipeIds(reloaded)).doesNotContain(annasRecipeId.intValue());
    }

    /**
     * A household profile names ingredients of whichever member filled it in. They still have to
     * keep a recipe out when somebody else generates the week.
     */
    @Test
    void aHouseholdProfileKeepsAvoidedIngredientsOut() throws Exception {
        var curry = mockMvc.perform(post("/api/v1/recipes")
                        .with(asUser(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Erdnusscurry\",\"servings\":2,\"neededIngredients\":"
                                + "[{\"amount\":100,\"unit\":\"g\",\"ingredient\":{\"name\":\"Erdnuss\"}}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer peanut = JsonPath.read(curry, "$.neededIngredients[0].ingredient.id");

        var draft = generateForHousehold(householdProfile(List.of(peanut)));

        assertThat(cookedRecipeIds(draft))
                .contains(annasRecipeId.intValue())
                .doesNotContain(JsonPath.<Integer>read(curry, "$.id"));
    }

    // ------------------------------------------------------------------------------- helpers

    private void planIntoOwnWeek(String emailAddress, Long recipeId) throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/" + DAY)
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipes\":[{\"type\":\"NORMAL_RECIPE\",\"id\":" + recipeId + "}]}"))
                .andExpect(status().isOk());
    }

    /** Checked on the stored rows, so the render guard cannot hide a cleanup that did not run. */
    private void assertOwnWeekIsEmpty(String emailAddress) {
        var owner = userRepository.findByEmailAddress(emailAddress);
        transactionTemplate.executeWithoutResult(status -> assertThat(
                weekplanDayRepository.findSingleDay(LocalDate.parse(DAY), PlanScope.of(owner)).getRecipes())
                .isEmpty());
    }

    private int householdProfile(List<Integer> avoidedIngredientIds) throws Exception {
        return saveProfile(post("/api/v1/planning/profiles").param("household", householdId).with(asUser(ANNA)),
                avoidedIngredientIds, false);
    }

    private int ownProfile(String emailAddress, boolean includeHouseholdRecipes) throws Exception {
        return saveProfile(post("/api/v1/planning/profiles").with(asUser(emailAddress)), List.of(),
                includeHouseholdRecipes);
    }

    private int saveProfile(MockHttpServletRequestBuilder request, List<Integer> avoidedIngredientIds,
            boolean includeHouseholdRecipes) throws Exception {
        var profile = """
                { "name": "Familienwoche", "householdSize": 3, "cooldownWeeks": 0,
                  "leftoversAllowed": false, "spreadVariety": true, "avoidedIngredientIds": %s,
                  "includeHouseholdRecipes": %s,
                  "meals": [ { "mealType": "DINNER", "days": { "MONDAY": "ANY", "TUESDAY": "ANY",
                    "WEDNESDAY": "ANY", "THURSDAY": "ANY", "FRIDAY": "ANY", "SATURDAY": "ANY",
                    "SUNDAY": "ANY" } } ] }
                """.formatted(avoidedIngredientIds, includeHouseholdRecipes);
        var created = mockMvc.perform(request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profile))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private String generateForHousehold(int profileId) throws Exception {
        return generate(post("/api/v1/planning/drafts").param("household", householdId).with(asUser(BERT)),
                profileId);
    }

    private String generateOwn(String emailAddress, int profileId) throws Exception {
        return generate(post("/api/v1/planning/drafts").with(asUser(emailAddress)), profileId);
    }

    private String generate(MockHttpServletRequestBuilder request, int profileId) throws Exception {
        return mockMvc.perform(request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + profileId + ",\"startDate\":\"" + DAY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<Integer> cookedRecipeIds(String draft) {
        return JsonPath.read(draft, "$.slots[?(@.kind == 'COOKED')].recipe.id");
    }

    private Long recipeOf(String emailAddress, String title) {
        return recipeRepository.save(Recipe.builder()
                .title(title).owner(userRepository.findByEmailAddress(emailAddress)).servings(2)
                .images(new ArrayList<>()).preparationSteps(new ArrayList<>())
                .neededIngredients(new ArrayList<>()).recipeGroups(new ArrayList<>())
                .build()).getId();
    }

    /** Removed, not left as a row nobody can open. */
    private void assertHouseholdWeekIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/weekplan/" + DAY + "/to/" + DAY)
                        .param("household", householdId)
                        .with(asUser(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipes.length()").value(0));
    }

    private void planIntoHousehold() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/" + DAY)
                        .param("household", householdId)
                        .with(asUser(BERT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipes\":[{\"type\":\"NORMAL_RECIPE\",\"id\":" + annasRecipeId + "}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].title").value("Annas Lasagne"));
    }

    private static RequestPostProcessor asUser(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name);
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

    private void join(String household, String emailAddress) throws Exception {
        var invite = mockMvc.perform(post("/api/v1/households/" + household + "/invites")
                        .with(asUser(ANNA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/household-invites/" + JsonPath.read(invite, "$.token") + "/accept")
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());
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
