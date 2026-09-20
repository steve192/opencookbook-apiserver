package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.entities.nutrition.NutritionQuality;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeNutritionSummaryRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutritionSummaries;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueChangedEvent;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;

/**
 * The stored nutrition summary, and the promise that makes it safe to rank a cookbook by: it is a
 * cache with a provenance, so anything that could move the numbers drops the row.
 */
@SpringBootTest(properties = "opencookbook.nutrition.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeNutritionSummaryIntegrationTest extends IntegrationTest {

    private static final String COOK = "summary-cook@example.com";
    private static final String SOUP = """
            { "title": "Tomatensuppe", "servings": 2,
              "neededIngredients": [
                { "amount": 500, "unit": "g", "ingredient": { "name": "Tomate" } },
                { "amount": 100, "unit": "g", "ingredient": { "name": "Sahne" } } ] }
            """;

    /** No background import of the shipped dataset. */
    @MockitoBean
    private NutritionDatasetImporter backgroundImporter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecipeNutritionSummaries summaries;
    @Autowired
    private RecipeNutritionSummaryRepository summaryRepository;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private ApplicationEventPublisher events;

    @BeforeEach
    void catalogue() throws Exception {
        clean();
        food("custom-tomato", "Tomate", 18f);
        food("custom-cream", "Sahne", 300f);
        indexUpdater.rebuild();
        TestAccounts.ensure(userRepository, COOK);
        mockMvc.perform(post("/api/v1/recipes").with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(SOUP))
                .andExpect(status().isOk());
    }

    @Test
    void aSummaryIsComputedOnceAndThenRead() {
        assertThat(summaryRepository.count()).isZero();

        var first = summaries.of(soup());

        assertThat(first.getQuality()).isEqualTo(NutritionQuality.COMPLETE);
        assertThat(first.isPerServingBasis()).isTrue();
        // 500 g tomato at 18 kcal and 100 g cream at 300 kcal, over 2 servings
        assertThat(first.getPerServing().getEnergyKcal()).isEqualTo((500 * 0.18f + 100 * 3f) / 2);
        assertThat(summaryRepository.count()).isEqualTo(1);

        // Read back, not computed again: compared against the stored row, since what is in memory
        // carries more precision than a timestamp(6) column keeps.
        var stored = summaryRepository.findById(first.getRecipeId()).orElseThrow().getComputedAt();
        assertThat(summaries.of(soup()).getComputedAt()).isEqualTo(stored);
        assertThat(summaryRepository.count()).isEqualTo(1);
    }

    /** What "another pasta dish" means when a week is spread over different foods. */
    @Test
    void theSummaryNamesTheFoodMostOfTheRecipeIs() {
        var summary = summaries.of(soup());

        assertThat(summary.getMainFood().getCatalogueKey()).isEqualTo("custom-tomato");
        assertThat(summary.getMainFoodGramShare()).isEqualTo(500f / 600f);
    }

    @Test
    void savingTheRecipeDropsWhatWasComputedForIt() throws Exception {
        summaries.of(soup());
        assertThat(summaryRepository.count()).isEqualTo(1);

        mockMvc.perform(put("/api/v1/recipes/" + soup().getId()).with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Tomatensuppe", "servings": 4,
                          "neededIngredients": [
                            { "amount": 500, "unit": "g", "ingredient": { "name": "Tomate" } } ],
                          "preparationSteps": [], "images": [], "recipeGroups": [] }
                        """))
                .andExpect(status().isOk());

        assertThat(summaryRepository.count()).isZero();
        // Recomputed from the recipe as it is now: one ingredient over four servings
        assertThat(summaries.of(soup()).getPerServing().getEnergyKcal()).isEqualTo(500 * 0.18f / 4);
    }

    /**
     * A relinked ingredient, a corrected custom food or a new dataset can all move any recipe's
     * values. Working out which recipes moved costs more than recomputing the ones anybody asks for.
     */
    @Test
    void aCatalogueChangeDropsEverythingComputedFromIt() {
        summaries.of(soup());
        assertThat(summaryRepository.count()).isEqualTo(1);

        events.publishEvent(new CatalogueChangedEvent("a food was corrected"));

        assertThat(summaryRepository.count()).isZero();
    }

    /** With its lines fetched: computing a summary reads them, and the test holds no session. */
    private Recipe soup() {
        return recipeRepository.findAllWithIngredients().stream()
                .filter(recipe -> recipe.getTitle().equals("Tomatensuppe"))
                .findFirst().orElseThrow();
    }

    private void food(String key, String german, float kcalPer100g) {
        foodRepository.save(CatalogueFood.builder()
                .catalogueKey(key)
                .origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(kcalPer100g).build())
                .names(new ArrayList<>(List.of(CatalogueFoodName.builder().languageIsoCode("de").name(german)
                        .display(true).origin(CatalogueFoodName.Origin.DATASET).build())))
                .build());
    }

    private void clean() {
        shareRepository.deleteAll();
        summaryRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
        foodRepository.deleteAll();
    }
}
