package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.CatalogueNameRuleRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRelinkProposalRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRelinkRunRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;

@SpringBootTest(properties = "opencookbook.nutrition.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminRelinkApiIntegrationTest extends IntegrationTest {

    private static final String OPERATOR = "relink-operator@example.com";
    private static final String COOK = "relink-cook@example.com";
    private static final String MAGIC = "Zauberpulver";
    private static final String RECIPE = """
            { "title": "Zaubertrank", "servings": 1,
              "neededIngredients": [
                { "amount": 100, "unit": "g", "ingredient": { "name": "Zucker" } },
                { "amount": 10, "unit": "g", "ingredient": { "name": "Zauberpulver" } } ] }
            """;

    /** No background import of the shipped dataset. */
    @MockitoBean
    private NutritionDatasetImporter backgroundImporter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private IngredientRelinkRunRepository runRepository;
    @Autowired
    private IngredientRelinkProposalRepository proposalRepository;
    @Autowired
    private CatalogueNameRuleRepository ruleRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;

    private CatalogueFood sugar;

    @BeforeEach
    void catalogue() throws Exception {
        clean();
        sugar = foodRepository.save(CatalogueFood.builder()
                .catalogueKey("custom-sugar")
                .origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(400f).build())
                .names(new ArrayList<>(List.of(name("de", "Zucker"), name("en", "Sugar"))))
                .build());
        indexUpdater.rebuild();
        account(OPERATOR);
        account(COOK);
        mockMvc.perform(post("/api/v1/recipes").with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(RECIPE))
                .andExpect(status().isOk());
    }

    @Test
    void aRunLinksWhatTheCatalogueLearnedAndIsRevertedAgain() throws Exception {
        catalogueLearnsTheMagicName();
        var runId = preview("NEVER_MATCHED_OR_UNLINKED");
        mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs/" + runId + "/proposals").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("zauberpulver"))
                .andExpect(jsonPath("$[0].change").value("NEW_LINK"))
                .andExpect(jsonPath("$[0].newFood.catalogueKey").value("custom-sugar"))
                .andExpect(jsonPath("$[0].ingredientCount").value(1));

        decideAll(runId, "ACCEPTED", false);
        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/apply").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedCount").value(1));
        assertThat(magic().getCatalogueFood()).isEqualTo(sugar);
        assertThat(magic().getLinkSource()).isEqualTo(Ingredient.LinkSource.AUTO);

        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/revert").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERTED"));
        assertThat(magic().getCatalogueFood()).isNull();
    }

    @Test
    void anIngredientItsOwnerLinkedAfterThePreviewIsSkipped() throws Exception {
        catalogueLearnsTheMagicName();
        var runId = preview("NEVER_MATCHED_OR_UNLINKED");
        decideAll(runId, "ACCEPTED", false);

        mockMvc.perform(put("/api/v1/ingredients/" + magic().getId() + "/link").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON).content("{\"excluded\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/apply").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(1));
        assertThat(magic().isExcludedFromNutrition()).isTrue();
    }

    @Test
    void aRememberedRejectionIsNotProposedAgain() throws Exception {
        catalogueLearnsTheMagicName();
        var runId = preview("NEVER_MATCHED_OR_UNLINKED");
        decideAll(runId, "REJECTED", true);

        mockMvc.perform(get("/api/v1/admin/nutrition/name-rules").with(operator()))
                .andExpect(jsonPath("$[0].name").value("zauberpulver"))
                .andExpect(jsonPath("$[0].kind").value("NEVER_LINK_TO"))
                .andExpect(jsonPath("$[0].food.catalogueKey").value("custom-sugar"));
        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/discard").with(operator()))
                .andExpect(jsonPath("$.status").value("DISCARDED"));

        var nextRunId = preview("NEVER_MATCHED_OR_UNLINKED");
        mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs/" + nextRunId + "/proposals").with(operator()))
                .andExpect(jsonPath("$[*].newFood.catalogueKey").value(not(hasItem("custom-sugar"))));
    }

    @Test
    void onlyAPreviewedRunIsApplied() throws Exception {
        var runId = preview("ALL_AUTOMATIC");
        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/discard").with(operator()));

        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/apply").with(operator()))
                .andExpect(status().isConflict());
    }

    @Test
    void aLinkTheMatcherIsNowSurerOfIsProposedWithItsNewConfidence() throws Exception {
        var zucker = ingredientRepository.findByNameAndOwner("Zucker", userRepository.findByEmailAddress(COOK)).orElseThrow();
        zucker.linkAutomatically(sugar, 0.7f, 1, java.time.Instant.EPOCH, null);
        ingredientRepository.saveAndFlush(zucker);

        var runId = preview("ALL_AUTOMATIC");
        mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs/" + runId + "/proposals").with(operator()))
                .andExpect(jsonPath("$[0].name").value("zucker"))
                .andExpect(jsonPath("$[0].change").value("CONFIDENCE"))
                .andExpect(jsonPath("$[0].band").value("SILENT"));
        decideAll(runId, "ACCEPTED", false);
        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/apply").with(operator()))
                .andExpect(jsonPath("$.appliedCount").value(1));

        assertThat(ingredientRepository.findById(zucker.getId()).orElseThrow().getLinkConfidence()).isGreaterThanOrEqualTo(0.9f);
    }

    @Test
    void aRunWithNothingAcceptedIsNotApplied() throws Exception {
        catalogueLearnsTheMagicName();
        var runId = preview("NEVER_MATCHED_OR_UNLINKED");

        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/apply").with(operator()))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs/" + runId).with(operator()))
                .andExpect(jsonPath("$.status").value("PREVIEWED"));
    }

    @Test
    void theScopeBelowAConfidenceNeedsTheConfidence() throws Exception {
        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\": \"AUTOMATIC_BELOW_CONFIDENCE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNameMarkedAsNoFoodIsNeitherProposedNorListedAsUnmatched() throws Exception {
        catalogueLearnsTheMagicName();
        mockMvc.perform(post("/api/v1/admin/nutrition/name-rules").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" ZAUBERPULVER \", \"kind\": \"NOT_A_FOOD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("zauberpulver"));

        var runId = preview("NEVER_MATCHED_OR_UNLINKED");
        mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs/" + runId + "/proposals").with(operator()))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/admin/nutrition/unmatched-names").with(operator()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anAdministratorLinksOneIngredient() throws Exception {
        mockMvc.perform(put("/api/v1/admin/ingredients/" + magic().getId() + "/link").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"catalogueFoodId\": " + sugar.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogueFoodKey").value("custom-sugar"))
                .andExpect(jsonPath("$.linkSource").value("ADMIN"));
    }

    @Test
    void unmatchedNamesAreListedWithTheMostLikelyFoods() throws Exception {
        mockMvc.perform(get("/api/v1/admin/nutrition/unmatched-names").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("zauberpulver"))
                .andExpect(jsonPath("$[0].language").value("de"))
                .andExpect(jsonPath("$[0].userCount").value(1))
                .andExpect(jsonPath("$[0].useCount").value(1));
    }

    @Test
    void theNamesExportIsADraftOfTheProductionGoldSet() throws Exception {
        mockMvc.perform(get("/api/v1/admin/nutrition/names-export").with(operator()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/tab-separated-values"))
                .andExpect(content().string(containsString("zauberpulver\tde\t?\t1\n")))
                .andExpect(content().string(containsString("zucker\tde\t?\t1\n")));
    }

    @Test
    void theCoverageReportCountsRecipesAndWarnings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/nutrition/coverage").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeCount").value(1))
                .andExpect(jsonPath("$.recipesByStatus.UNAVAILABLE").value(1))
                .andExpect(jsonPath("$.warningCauses[0].key").value("UNLINKED"))
                .andExpect(jsonPath("$.namesCausingWarnings[0].key").value("zauberpulver"));
    }

    @Test
    void aUsersCorrectionIsListedUntilTheCatalogueAgrees() throws Exception {
        mockMvc.perform(put("/api/v1/ingredients/" + magic().getId() + "/link").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON).content("{\"catalogueFoodId\": " + sugar.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/nutrition/user-corrections").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("zauberpulver"))
                .andExpect(jsonPath("$[0].food.catalogueKey").value("custom-sugar"))
                .andExpect(jsonPath("$[0].userCount").value(1))
                .andExpect(jsonPath("$[0].matcherFood").value(nullValue()));

        catalogueLearnsTheMagicName();
        mockMvc.perform(get("/api/v1/admin/nutrition/user-corrections").with(operator()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aNameUsersExcludedIsListedAsCandidateForNoFood() throws Exception {
        var sugarIngredient = ingredientRepository.findByNameAndOwner("Zucker", userRepository.findByEmailAddress(COOK)).orElseThrow();
        mockMvc.perform(put("/api/v1/ingredients/" + sugarIngredient.getId() + "/link").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON).content("{\"excluded\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/nutrition/user-corrections").with(operator()))
                .andExpect(jsonPath("$[0].name").value("zucker"))
                .andExpect(jsonPath("$[0].food").value(nullValue()))
                .andExpect(jsonPath("$[0].matcherFood.catalogueKey").value("custom-sugar"));
    }

    @Test
    void cooksHaveNoCockpit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs").with(user(COOK)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/nutrition/coverage").with(user(COOK)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/nutrition/user-corrections").with(user(COOK)))
                .andExpect(status().isForbidden());
    }

    /** Other tests delete foods and ingredients unaware of runs and rules. */
    @AfterEach
    void clean() {
        shareRepository.deleteAll();
        recipeRepository.deleteAll();
        proposalRepository.deleteAll();
        ingredientRepository.deleteAll();
        runRepository.deleteAll();
        ruleRepository.deleteAll();
        foodRepository.deleteAll();
    }

    private void catalogueLearnsTheMagicName() {
        sugar.getNames().add(CatalogueFoodName.builder().languageIsoCode("de").name(MAGIC).display(false)
                .origin(CatalogueFoodName.Origin.ADMIN).build());
        sugar = foodRepository.save(sugar);
        indexUpdater.rebuild();
    }

    private long preview(String scope) throws Exception {
        var body = mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\": \"" + scope + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void decideAll(long runId, String decision, boolean remember) throws Exception {
        var proposals = mockMvc.perform(get("/api/v1/admin/nutrition/relink-runs/" + runId + "/proposals").with(operator()))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(proposals, "$[*].id");
        mockMvc.perform(post("/api/v1/admin/nutrition/relink-runs/" + runId + "/decisions").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proposalIds\": " + ids + ", \"decision\": \"" + decision + "\", \"remember\": " + remember + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(ids.size()));
    }

    private Ingredient magic() {
        return ingredientRepository.findByNameAndOwner(MAGIC, userRepository.findByEmailAddress(COOK)).orElseThrow();
    }

    private static CatalogueFoodName name(String language, String name) {
        return CatalogueFoodName.builder().languageIsoCode(language).name(name).display(true)
                .origin(CatalogueFoodName.Origin.ADMIN).build();
    }

    private static RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
    }

    private void account(String emailAddress) {
        if (userRepository.findByEmailAddress(emailAddress) == null) {
            var user = new CookpalUser();
            user.setEmailAddress(emailAddress);
            user.setPasswordHash("irrelevant");
            user.setActivated(true);
            user.setLanguage("de");
            userRepository.save(user);
        }
    }
}
