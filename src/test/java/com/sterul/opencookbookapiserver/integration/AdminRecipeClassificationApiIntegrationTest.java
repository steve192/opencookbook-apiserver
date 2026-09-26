package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.DerivedClassificationRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeClassificationProposalRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeClassificationRunRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;

/** Deriving recipe diets under review, and the rules that make a run safe to run. */
@SpringBootTest(properties = "opencookbook.nutrition.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminRecipeClassificationApiIntegrationTest extends IntegrationTestBase {

    private static final String OPERATOR = "classification-operator@example.com";
    private static final String COOK = "classification-cook@example.com";
    private static final String RUNS = "/api/v1/admin/recipes/classification-runs";

    private static final String BOLOGNESE = """
            { "title": "Bolognese", "servings": 2,
              "neededIngredients": [
                { "amount": 200, "unit": "g", "ingredient": { "name": "Hackfleisch" } },
                { "amount": 400, "unit": "g", "ingredient": { "name": "Tomate" } } ] }
            """;
    private static final String OFENGEMUESE = """
            { "title": "Ofengemuese", "servings": 2,
              "neededIngredients": [ { "amount": 400, "unit": "g", "ingredient": { "name": "Tomate" } } ] }
            """;
    private static final String OMAS_GULASCH = """
            { "title": "Omas Gulasch", "servings": 2,
              "neededIngredients": [
                { "amount": 200, "unit": "g", "ingredient": { "name": "Hackfleisch" } },
                { "amount": 5, "unit": "g", "ingredient": { "name": "Omas Gewuerz" } } ] }
            """;

    /** No background import of the shipped dataset. */
    @MockitoBean
    private NutritionDatasetImporter backgroundImporter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeClassificationRunRepository runRepository;
    @Autowired
    private RecipeClassificationProposalRepository proposalRepository;
    @Autowired
    private DerivedClassificationRepository derivedRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void catalogue() {
        clean();
        food("custom-mince", "Hackfleisch", "Minced meat", Diet.MEAT);
        food("custom-tomato", "Tomate", "Tomato", Diet.VEGAN);
        indexUpdater.rebuild();
        TestAccounts.ensure(userRepository, OPERATOR);
        TestAccounts.ensure(userRepository, COOK);
    }

    /** While a recipe is written, its diet is read the way saving would link it, and nothing is kept. */
    @Test
    void aRecipeBeingWrittenGetsItsDietFromItsIngredients() throws Exception {
        var ingredientsBefore = ingredientRepository.count();

        assertThat(previewDiet("[\"Hackfleisch\", \"Tomate\"]")).isEqualTo("MEAT");
        assertThat(previewDiet("[\"Tomate\"]")).isEqualTo("VEGAN");
        assertThat(previewDiet("[\"Tomate\", \"Omas Gewuerz\"]")).isNull();
        assertThat(ingredientRepository.count()).isEqualTo(ingredientsBefore);
    }

    private String previewDiet(String ingredientNames) throws Exception {
        var response = mockMvc.perform(post("/api/v1/recipes/diet-preview").with(user(COOK))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ingredientNames\": " + ingredientNames + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.diet");
    }

    @Test
    void aRunDerivesDietsFromTheCatalogueAndIsRevertedAgain() throws Exception {
        createRecipe(BOLOGNESE);
        createRecipe(OFENGEMUESE);

        var runId = preview("NEVER_CLASSIFIED");
        // Proposals come back in the order they were written, which is the order the recipes were created
        mockMvc.perform(get(RUNS + "/" + runId + "/proposals").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].recipeTitle").value("Bolognese"))
                .andExpect(jsonPath("$[0].proposedValue").value("MEAT"))
                .andExpect(jsonPath("$[0].reason").value("MEAT because of Hackfleisch"))
                .andExpect(jsonPath("$[1].recipeTitle").value("Ofengemuese"))
                .andExpect(jsonPath("$[1].proposedValue").value("VEGAN"));

        acceptAll(runId);
        mockMvc.perform(post(RUNS + "/" + runId + "/apply").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedCount").value(2));
        assertThat(diet("Bolognese")).isEqualTo(Diet.MEAT);
        assertThat(derivedBy("Bolognese")).contains(runId);

        revert(runId);
        assertThat(diet("Bolognese")).isNull();
        assertThat(derivedBy("Bolognese")).isEmpty();
    }

    /** Reverting a re-read hands the value back to the run that derived it before, not to nobody. */
    @Test
    void revertingAReReadRestoresTheEarlierRun() throws Exception {
        createRecipe(BOLOGNESE);
        var first = applyAll(preview("NEVER_CLASSIFIED"));
        var reRead = applyAll(preview("DERIVED_ONLY"));
        assertThat(derivedBy("Bolognese")).contains(reRead);

        revert(reRead);

        assertThat(diet("Bolognese")).isEqualTo(Diet.MEAT);
        assertThat(derivedBy("Bolognese")).contains(first);
    }

    /** Saving a recipe to fix its title is not a decision about its diet; changing the diet is. */
    @Test
    void onlyChangingADerivedDietMakesItTheCooks() throws Exception {
        createRecipe(BOLOGNESE);
        var runId = applyAll(preview("NEVER_CLASSIFIED"));

        saveAsOwner("Bolognese", Diet.MEAT);
        assertThat(derivedBy("Bolognese")).contains(runId);

        saveAsOwner("Bolognese", Diet.VEGETARIAN);
        assertThat(derivedBy("Bolognese")).isEmpty();
        mockMvc.perform(get(RUNS + "/" + preview("DERIVED_ONLY") + "/proposals").with(operator()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** One ingredient nobody linked is enough: the recipe is reported, not guessed at. */
    @Test
    void aRecipeWithAnUnlinkedIngredientIsSkippedAndSaysWhich() throws Exception {
        createRecipe(OMAS_GULASCH);

        var runId = preview("NEVER_CLASSIFIED");
        mockMvc.perform(get(RUNS + "/" + runId + "/proposals").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decision").value("SKIPPED"))
                .andExpect(jsonPath("$[0].proposedValue").doesNotExist())
                .andExpect(jsonPath("$[0].reason").value("not linked to the catalogue: Omas Gewuerz"));

        mockMvc.perform(get(RUNS + "/" + runId).with(operator()))
                .andExpect(jsonPath("$.unreadableCount").value(1));

        // A skipped proposal cannot be accepted, so there is nothing to apply
        acceptAll(runId);
        mockMvc.perform(post(RUNS + "/" + runId + "/apply").with(operator()))
                .andExpect(status().isConflict());
        assertThat(diet("Omas Gulasch")).isNull();
    }

    /** A run must never overwrite what the cook chose. */
    @Test
    void aDietTheCookSetIsNeverInScope() throws Exception {
        createRecipe(BOLOGNESE);
        saveAsOwner("Bolognese", Diet.VEGETARIAN);

        var runId = preview("NEVER_CLASSIFIED");

        mockMvc.perform(get(RUNS + "/" + runId + "/proposals").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(diet("Bolognese")).isEqualTo(Diet.VEGETARIAN);
        assertThat(derivedBy("Bolognese")).isEmpty();
    }

    private int preview(String scope) throws Exception {
        var response = mockMvc.perform(post(RUNS).with(operator()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"DIET\", \"scope\": \"" + scope + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void acceptAll(int runId) throws Exception {
        var response = mockMvc.perform(get(RUNS + "/" + runId + "/proposals").with(operator()))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(response, "$[*].id");
        mockMvc.perform(post(RUNS + "/" + runId + "/decisions").with(operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalIds\": " + ids + ", \"decision\": \"ACCEPTED\"}"))
                .andExpect(status().isOk());
    }

    private int applyAll(int runId) throws Exception {
        acceptAll(runId);
        mockMvc.perform(post(RUNS + "/" + runId + "/apply").with(operator())).andExpect(status().isOk());
        return runId;
    }

    private void revert(int runId) throws Exception {
        mockMvc.perform(post(RUNS + "/" + runId + "/revert").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERTED"));
    }

    private void createRecipe(String body) throws Exception {
        mockMvc.perform(post("/api/v1/recipes").with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    /** Through the API, exactly as the app saves a recipe. */
    private void saveAsOwner(String title, Diet diet) throws Exception {
        var recipe = recipe(title);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/recipes/" + recipe.getId()).with(user(COOK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\", \"servings\": 2, \"recipeType\": \"" + diet + "\","
                                + " \"neededIngredients\": [], \"preparationSteps\": [], \"images\": [],"
                                + " \"recipeGroups\": []}"))
                .andExpect(status().isOk());
    }

    private Recipe recipe(String title) {
        return recipeRepository.findAll().stream().filter(candidate -> candidate.getTitle().equals(title))
                .findFirst().orElseThrow();
    }

    private Diet diet(String title) {
        return recipe(title).getRecipeType();
    }

    /** The id of the run that derived the recipe's diet; empty where a person chose it. */
    private Optional<Integer> derivedBy(String title) {
        return transactionTemplate.execute(status -> derivedRepository
                .findByRecipeAndKind(recipe(title), ClassificationKind.DIET)
                .map(mark -> mark.getRun().getId().intValue()));
    }

    private void food(String key, String german, String english, Diet dietClass) {
        foodRepository.save(CatalogueFood.builder()
                .catalogueKey(key)
                .origin(CatalogueFood.Origin.CUSTOM)
                .dietClass(dietClass)
                .dietClassOrigin(CatalogueFood.DietClassOrigin.DATASET)
                .nutrients(NutrientValues.builder().energyKcal(100f).build())
                .names(new ArrayList<>(List.of(name("de", german), name("en", english))))
                .build());
    }

    private static CatalogueFoodName name(String language, String text) {
        return CatalogueFoodName.builder().languageIsoCode(language).name(text).display(true)
                .origin(CatalogueFoodName.Origin.DATASET).build();
    }

    private RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
    }

    /** Proposals and marks before recipes, and recipes before the runs they point back at. */
    private void clean() {
        shareRepository.deleteAll();
        proposalRepository.deleteAll();
        derivedRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
        runRepository.deleteAll();
        foodRepository.deleteAll();
    }
}
