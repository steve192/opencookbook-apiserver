package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestCatalogue.food;
import static com.sterul.opencookbookapiserver.integration.TestCatalogue.name;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.ResultMatcher;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueIndexUpdater;

import jakarta.persistence.EntityManagerFactory;

/** The cookbook listing and the nutrition sheets cost the same number of queries however many recipes they hold. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeListingQueryIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "anna@example.invalid";
    private static final String BERT = "bert@example.invalid";
    private static final int FEW_EACH = 1;
    private static final int MORE_EACH = 20;
    private static final int LINES_PER_RECIPE = 2;
    private static final String RECIPE = """
            { "title": "%s", "servings": 2,
              "neededIngredients": [
                { "amount": 500, "unit": "g", "ingredient": { "name": "Weizenmehl" } },
                { "amount": 2, "unit": "", "ingredient": { "name": "Eier" } } ] }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeImageRepository imageRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
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
        ingredientRepository.deleteAll();
        foodRepository.deleteAll();

        anna = TestAccounts.ensure(userRepository, ANNA);
        bert = TestAccounts.ensure(userRepository, BERT);
        addRecipes(FEW_EACH);
        householdId = TestHouseholds.start(mockMvc, ANNA, "Familie Test");
        TestHouseholds.join(mockMvc, householdId, ANNA, BERT);
        TestHouseholds.share(mockMvc, householdId, BERT);
    }

    // Both owners are in both listings, so only the number of recipes differs.
    @Test
    void moreRecipesCostNoMoreQueries() throws Exception {
        var few = statementsFor(2 * FEW_EACH);
        addRecipes(MORE_EACH);
        var many = statementsFor(2 * (FEW_EACH + MORE_EACH));

        assertEquals(few, many, "a query per recipe on the page");
    }

    // Each recipe has a line by weight and a line by piece, both linked, so ingredients, foods and portions are read.
    @Test
    void moreRecipesWithLinkedIngredientsCostNoMoreQueries() throws Exception {
        linkableFoods();
        postRecipes(FEW_EACH);
        var listingFew = listingStatements(2 * FEW_EACH);
        var sheetsFew = sheetStatements(2 * FEW_EACH);
        postRecipes(MORE_EACH);
        var listingMany = listingStatements(2 * (FEW_EACH + MORE_EACH));
        var sheetsMany = sheetStatements(2 * (FEW_EACH + MORE_EACH));

        assertAll(
                () -> assertEquals(listingFew, listingMany, "a query per recipe or line for the nutrition in the listing"),
                () -> assertEquals(sheetsFew, sheetsMany, "a query per recipe or line for the nutrition sheets"));
    }

    private void addRecipes(int each) {
        for (var index = 0; index < each; index++) {
            recipeWithImage(anna, "Anna " + index);
            recipeWithImage(bert, "Bert " + index);
        }
    }

    private long statementsFor(int recipesListed) throws Exception {
        return statementsOf("/api/v1/recipes",
                jsonPath("$.length()").value(recipesListed),
                jsonPath("$[0].images[0].uuid").isNotEmpty(),
                jsonPath("$[?(@.mine == false)].ownerDisplayName").isNotEmpty(),
                jsonPath("$[0].householdIds[0]").value(householdId));
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

    private void linkableFoods() {
        food(foodRepository, "custom-flour", 350, null, name("de", "Weizenmehl"), name("en", "Wheat flour"));
        food(foodRepository, "custom-egg", 135,
                CatalogueFoodPortion.builder().unitKey("piece").grams(60).origin(CatalogueFoodPortion.Origin.SOURCE).build(),
                name("de", "Eier"), name("en", "Egg"));
        indexUpdater.rebuild();
    }

    private void postRecipes(int each) throws Exception {
        for (var index = 0; index < each; index++) {
            postRecipe(ANNA, "Anna " + index);
            postRecipe(BERT, "Bert " + index);
        }
    }

    private void postRecipe(String owner, String title) throws Exception {
        mockMvc.perform(post("/api/v1/recipes").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RECIPE.formatted(title)))
                .andExpect(status().isOk());
    }

    private long listingStatements(int linkedRecipes) throws Exception {
        return statementsOf("/api/v1/recipes",
                jsonPath("$[?(@.nutrition.values.energyKcal > 0)]").value(hasSize(linkedRecipes)));
    }

    private long sheetStatements(int linkedRecipes) throws Exception {
        return statementsOf("/api/v1/recipes/nutrition",
                jsonPath("$[*].nutrition.lines[*].food.displayName").value(hasSize(LINES_PER_RECIPE * linkedRecipes)));
    }

    private long statementsOf(String path, ResultMatcher... expectations) throws Exception {
        var statistics = statistics();
        statistics.clear();
        var response = mockMvc.perform(get(path).with(user(ANNA)));
        var statements = statistics.getPrepareStatementCount();
        response.andExpect(status().isOk()).andExpectAll(expectations);
        return statements;
    }
}
