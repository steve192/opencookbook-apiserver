package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestCatalogue.name;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.baking.SoakingFood;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonCategory;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonMonth;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonMonth.GrowingMethod;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonalProduce;
import com.sterul.opencookbookapiserver.entities.seasonal.StorageFacts;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.SeasonalProduceRepository;
import com.sterul.opencookbookapiserver.repositories.SoakingFoodRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueIndexUpdater;

/**
 * The catalogue as the kitchen tools read it on an instance whose tools are public. The budgets are
 * turned down to something a test can reach; each test calls from its own address or account, as
 * budgets are kept per address and per account. Other test classes may import the shipped dataset into the same database,
 * so answers are searched for the test's own food rather than read by position.
 */
@SpringBootTest(properties = {
        "opencookbook.tools.public=true",
        "opencookbook.rate-limits.cheap.anonymous-per-hour=3",
        "opencookbook.rate-limits.cheap.signed-in-per-hour=5"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class KitchenToolsIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "tools-cook@example.invalid";

    private static final String ASPARAGUS = "$[?(@.displayName == 'Asparagus')]";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private SeasonalProduceRepository seasonalProduceRepository;
    @Autowired
    private SoakingFoodRepository soakingFoodRepository;

    private CatalogueFood asparagus;

    /** Each fact is checked on its own: a dataset import elsewhere replaces every feature's facts. */
    @BeforeEach
    void setup() {
        asparagus = foodRepository.findByCatalogueKey("custom-asparagus").orElseGet(() -> {
            var food = foodRepository.save(CatalogueFood.builder()
                    .catalogueKey("custom-asparagus")
                    .origin(CatalogueFood.Origin.CUSTOM)
                    .nutrients(NutrientValues.builder().energyKcal(18f).carbohydrates(2f).sugar(1.5f).build())
                    .waterGPer100g(93.6f)
                    .aisle(Aisle.FRUIT_VEG)
                    .names(new ArrayList<>(List.of(name("de", "Spargel"), name("en", "Asparagus"))))
                    .build());
            indexUpdater.rebuild();
            return food;
        });
        if (!seasonalProduceRepository.existsById(asparagus.getId())) {
            seasonalProduceRepository.save(SeasonalProduce.builder()
                    .food(asparagus)
                    .category(SeasonCategory.VEGETABLE)
                    .months(new HashSet<>(Set.of(
                            new SeasonMonth("DE", (short) 4, GrowingMethod.PROTECTED),
                            new SeasonMonth("DE", (short) 5, GrowingMethod.FIELD),
                            new SeasonMonth("DE", (short) 5, GrowingMethod.PROTECTED))))
                    .storage(StorageFacts.builder().place(StorageFacts.Place.FRIDGE).build())
                    .build());
        }
        if (!soakingFoodRepository.existsById(asparagus.getId())) {
            soakingFoodRepository.save(SoakingFood.builder().food(asparagus).absorption(0.5f).build());
        }
    }

    @Test
    void theInstanceSaysItsToolsArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolsPublic").value(true));
    }

    @Test
    void theSeasonalCalendarAnswersWithoutAnAccountAndMayBeCached() throws Exception {
        mockMvc.perform(from("10.0.0.1", get("/api/v1/catalogue/seasons").param("lang", "en")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                .andExpect(jsonPath(ASPARAGUS + ".category").value(contains("VEGETABLE")))
                .andExpect(jsonPath(ASPARAGUS + ".months.4").value(contains(contains("PROTECTED"))))
                .andExpect(jsonPath(ASPARAGUS + ".months.5").value(contains(contains("FIELD", "PROTECTED"))))
                .andExpect(jsonPath(ASPARAGUS + ".inSeason").value(contains(contains(4, 5))))
                .andExpect(jsonPath(ASPARAGUS + ".storage.place").value(contains("FRIDGE")));
    }

    @Test
    void theSoakingFoodsAnswerWithoutAnAccountAndMayBeCached() throws Exception {
        mockMvc.perform(from("10.0.0.7", get("/api/v1/catalogue/soaking")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                .andExpect(jsonPath("$[?(@.foodId == " + asparagus.getId() + ")].absorption").value(contains(0.5)));
    }

    @Test
    void theSeasonalCalendarNeedsALanguageCode() throws Exception {
        mockMvc.perform(from("10.0.0.6", get("/api/v1/catalogue/seasons").param("lang", "english")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(from("10.0.0.6", get("/api/v1/catalogue/seasons")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAnonymousSearchShowsWhatTheBreadToolNeeds() throws Exception {
        mockMvc.perform(from("10.0.0.2", get("/api/v1/catalogue/search").param("q", "Spargel").param("lang", "de")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Spargel"))
                .andExpect(jsonPath("$[0].waterPer100g").value(93.6))
                .andExpect(jsonPath("$[0].starchPer100g").value(0.5))
                .andExpect(jsonPath("$[0].aisle").value("FRUIT_VEG"));
    }

    @Test
    void anonymousSearchesSpendTheBudgetAndARefusalSaysWhenToComeBack() throws Exception {
        for (var request = 0; request < 3; request++) {
            mockMvc.perform(from("10.0.0.3", get("/api/v1/catalogue/search").param("q", "Spargel")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(from("10.0.0.3", get("/api/v1/catalogue/search").param("q", "Spargel")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        mockMvc.perform(from("10.0.0.3", get("/api/v1/catalogue/seasons").param("lang", "en")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void ipv6CallersCountByTheirSlash64() throws Exception {
        for (var host = 1; host <= 3; host++) {
            mockMvc.perform(from("2001:db8:7:7::" + host, get("/api/v1/catalogue/seasons").param("lang", "en")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(from("2001:db8:7:7::99", get("/api/v1/catalogue/seasons").param("lang", "en")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void signedInReadsSpendABudgetOfTheirOwnPerAccount() throws Exception {
        var reader = user("tools-reader@example.invalid");
        for (var request = 0; request < 5; request++) {
            mockMvc.perform(from("10.0.0.4", get("/api/v1/catalogue/search").param("q", "Spargel").with(reader)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(from("10.0.0.5", get("/api/v1/catalogue/seasons").param("lang", "de").with(reader)))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(from("10.0.0.4", get("/api/v1/catalogue/seasons").param("lang", "de")))
                .andExpect(status().isOk());
    }

    @Test
    void whatToCookFindsRecipesWithWhatIsInSeason() throws Exception {
        var cook = TestAccounts.ensure(userRepository, COOK);
        var linked = ingredientRepository.save(Ingredient.builder().name("Grüner Spargel").owner(cook)
                .catalogueFood(asparagus).linkSource(Ingredient.LinkSource.USER).build());
        recipeRepository.save(recipe("Spargelrisotto", cook, linked));
        recipeRepository.save(recipe("Nudeln", cook, ingredientRepository.save(
                Ingredient.builder().name("Nudeln").owner(cook).build())));

        mockMvc.perform(post("/api/v1/recipes/suggestions").with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inSeasonMonth\": 5, \"includeHouseholdRecipes\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].recipe.title").value("Spargelrisotto"));
        mockMvc.perform(post("/api/v1/recipes/suggestions").with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inSeasonMonth\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1));
        mockMvc.perform(post("/api/v1/recipes/suggestions").with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inSeasonMonth\": 11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
        mockMvc.perform(post("/api/v1/recipes/suggestions").with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inSeasonMonth\": 5, \"mode\": \"MUST_CONTAIN\"}"))
                .andExpect(status().isBadRequest());
    }

    private static Recipe recipe(String title, CookpalUser owner, Ingredient ingredient) {
        return Recipe.builder().title(title).owner(owner).servings(2)
                .images(new ArrayList<>()).preparationSteps(new ArrayList<>()).recipeGroups(new ArrayList<>())
                .neededIngredients(new ArrayList<>(List.of(IngredientNeed.builder().amount(1f).unit("Bund")
                        .ingredient(ingredient).build())))
                .build();
    }

    private static MockHttpServletRequestBuilder from(String address, MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(address);
            return servletRequest;
        });
    }
}
