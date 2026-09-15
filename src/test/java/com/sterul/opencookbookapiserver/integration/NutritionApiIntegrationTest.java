package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;

/** Estimated nutrients of recipes, and how owners correct what their ingredients are. */
@SpringBootTest(properties = "opencookbook.nutrition.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class NutritionApiIntegrationTest extends IntegrationTest {

    private static final String COOK = "nutrition-cook@example.com";
    private static final String STRANGER = "nutrition-stranger@example.com";
    private static final String RECIPE = """
            { "title": "Pfannkuchen", "servings": 2,
              "neededIngredients": [
                { "amount": 500, "unit": "g", "ingredient": { "name": "Weizenmehl" } },
                { "amount": 2, "unit": "", "ingredient": { "name": "Eier" } },
                { "amount": 50, "unit": "g", "ingredient": { "name": "Zauberpulver" } } ] }
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
    private RecipeRepository recipeRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;

    private CatalogueFood sugar;

    @BeforeEach
    void catalogue() {
        shareRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
        foodRepository.deleteAll();
        food("custom-flour", 350, null, name("de", "Weizenmehl"), name("en", "Wheat flour"));
        food("custom-egg", 135, CatalogueFoodPortion.builder().unitKey("piece").grams(60).origin(CatalogueFoodPortion.Origin.SOURCE).build(),
                name("de", "Hühnerei"), name("de", "Eier"), name("en", "Egg"));
        sugar = food("custom-sugar", 400, null, name("de", "Zucker"), name("en", "Sugar"));
        indexUpdater.rebuild();
        account(COOK);
        account(STRANGER);
    }

    @Test
    void aSavedRecipesIngredientsAreLinkedAndItsNutrientsEstimated() throws Exception {
        mockMvc.perform(post("/api/v1/recipes").with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(RECIPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.basis").value("SERVING"))
                .andExpect(jsonPath("$.nutrition.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.nutrition.warningCount").value(1))
                .andExpect(jsonPath("$.nutrition.values.energyKcal").value(closeTo((5 * 350 + 1.2 * 135) / 2, 0.1)));
    }

    @Test
    void theNutritionSheetExplainsEveryLine() throws Exception {
        var recipeId = createRecipe();

        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/nutrition").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].food.displayName").value("Weizenmehl"))
                .andExpect(jsonPath("$.lines[0].grams").value(500.0))
                .andExpect(jsonPath("$.lines[0].linkSource").value("AUTO"))
                .andExpect(jsonPath("$.lines[1].grams").value(120.0))
                .andExpect(jsonPath("$.lines[2].status").value("UNLINKED"))
                .andExpect(jsonPath("$.lines[2].warns").value(true))
                .andExpect(jsonPath("$.attributions[?(@.source=='BLS')].licenseUrl").value("https://creativecommons.org/licenses/by/4.0/"));
    }

    @Test
    void somebodyElsesRecipeHasNoNutritionSheetForYou() throws Exception {
        var recipeId = createRecipe();

        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/nutrition").with(user(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOwnerLinksAnIngredientAndTheRecipeIsComplete() throws Exception {
        var recipeId = createRecipe();
        var magic = ingredientRepository.findByNameAndOwner("Zauberpulver", userRepository.findByEmailAddress(COOK)).orElseThrow();

        mockMvc.perform(put("/api/v1/ingredients/" + magic.getId() + "/link").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogueFoodId\": " + sugar.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.food.displayName").value("Zucker"))
                .andExpect(jsonPath("$.linkSource").value("USER"));

        mockMvc.perform(get("/api/v1/recipes/" + recipeId).with(user(COOK)))
                .andExpect(jsonPath("$.nutrition.status").value("COMPLETE"));
    }

    @Test
    void anOwnerSaysAnIngredientCountsForNothing() throws Exception {
        var recipeId = createRecipe();
        var magic = ingredientRepository.findByNameAndOwner("Zauberpulver", userRepository.findByEmailAddress(COOK)).orElseThrow();

        mockMvc.perform(put("/api/v1/ingredients/" + magic.getId() + "/link").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"excluded\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excluded").value(true));

        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/nutrition").with(user(COOK)))
                .andExpect(jsonPath("$.lines[2].status").value("EXCLUDED"))
                .andExpect(jsonPath("$.summary.status").value("COMPLETE"));
    }

    @Test
    void anOwnerSaysWhatAPieceWeighsAndTakesItBack() throws Exception {
        var recipeId = createRecipe();
        var eggs = ingredientRepository.findByNameAndOwner("Eier", userRepository.findByEmailAddress(COOK)).orElseThrow();

        mockMvc.perform(put("/api/v1/ingredients/" + eggs.getId() + "/portion").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unit\": \"\", \"grams\": 70}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/nutrition").with(user(COOK)))
                .andExpect(jsonPath("$.lines[1].grams").value(140.0))
                .andExpect(jsonPath("$.lines[1].ownPortion").value(true));

        mockMvc.perform(delete("/api/v1/ingredients/" + eggs.getId() + "/portion").with(user(COOK)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/nutrition").with(user(COOK)))
                .andExpect(jsonPath("$.lines[1].grams").value(120.0))
                .andExpect(jsonPath("$.lines[1].ownPortion").value(false));
    }

    @Test
    void onlyAUnitCountingPiecesHasAWeightPerPiece() throws Exception {
        createRecipe();
        var flour = ingredientRepository.findByNameAndOwner("Weizenmehl", userRepository.findByEmailAddress(COOK)).orElseThrow();

        mockMvc.perform(put("/api/v1/ingredients/" + flour.getId() + "/portion").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unit\": \"g\", \"grams\": 5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aLinkNeedsEitherAFoodOrToBeExcluded() throws Exception {
        createRecipe();
        var magic = ingredientRepository.findByNameAndOwner("Zauberpulver", userRepository.findByEmailAddress(COOK)).orElseThrow();

        mockMvc.perform(put("/api/v1/ingredients/" + magic.getId() + "/link").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogueFoodId\": " + sugar.getId() + ", \"excluded\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nobodyLinksSomebodyElsesIngredient() throws Exception {
        createRecipe();
        var magic = ingredientRepository.findByNameAndOwner("Zauberpulver", userRepository.findByEmailAddress(COOK)).orElseThrow();

        mockMvc.perform(put("/api/v1/ingredients/" + magic.getId() + "/link").with(user(STRANGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogueFoodId\": " + sugar.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCatalogueIsSearchedMostLikelyFoodFirst() throws Exception {
        mockMvc.perform(get("/api/v1/catalogue/search").param("q", "Zucker").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Zucker"))
                .andExpect(jsonPath("$[0].energyKcal").value(400.0));
    }

    @Test
    void theCataloguesNamesAreSuggestedWithoutIdsNextToTheUsersOwnIngredients() throws Exception {
        createRecipe();

        mockMvc.perform(get("/api/v1/ingredients").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Zauberpulver')].id").value(hasItem(not(nullValue()))))
                .andExpect(jsonPath("$[?(@.name=='Zucker')].id").value(hasItem(nullValue())));
    }

    @Test
    void aSharedRecipeShowsItsNutritionValuesButNoIds() throws Exception {
        var recipeId = createRecipe();
        var share = mockMvc.perform(post("/api/v1/shares").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipeId\": " + recipeId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/shared/" + JsonPath.read(share, "$.shareId")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.status").value("INCOMPLETE"));
        mockMvc.perform(get("/api/v1/shared/" + JsonPath.read(share, "$.shareId") + "/nutrition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.lines[0].food.displayName").isNotEmpty())
                .andExpect(jsonPath("$.lines[0].needId").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].ingredientId").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].linkSource").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].ownPortion").value(nullValue()));
    }

    private long createRecipe() throws Exception {
        var body = mockMvc.perform(post("/api/v1/recipes").with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(RECIPE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private CatalogueFood food(String key, float kcal, CatalogueFoodPortion portion, CatalogueFoodName... names) {
        return foodRepository.save(CatalogueFood.builder()
                .catalogueKey(key)
                .origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(kcal).build())
                .names(new ArrayList<>(List.of(names)))
                .portions(portion == null ? new ArrayList<>() : new ArrayList<>(List.of(portion)))
                .build());
    }

    private static CatalogueFoodName name(String language, String name) {
        return CatalogueFoodName.builder().languageIsoCode(language).name(name).display(true)
                .origin(CatalogueFoodName.Origin.ADMIN).build();
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
