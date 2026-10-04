package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.google.gson.Gson;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.recipeimport.GoogleShareLinkResolver;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.instagram.InstagramImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScraperServiceProxy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeImportApiIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "import-cook@example.invalid";

    private static final String RECIPE_TEXT = """
            Apfelkuchen
            Zutaten für 4 Personen:
            200 g Mehl
            100 g Zucker
            3 Äpfel
            Zubereitung:
            1. Alles verrühren.
            2. 40 Minuten backen.
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;

    @MockitoBean
    private RecipeScraperServiceProxy recipeScraperServiceProxy;
    @MockitoBean
    private InstagramImporter instagramImporter;
    @MockitoSpyBean
    private GoogleShareLinkResolver shareLinks;

    private CookpalUser cook;

    @BeforeEach
    void setup() {
        cook = TestAccounts.ensure(userRepository, COOK);
    }

    @Test
    void aRecipeTextBecomesADraftThatIsNotSaved() throws Exception {
        var recipesBefore = recipeRepository.count();

        importing(RECIPE_TEXT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.recipe.id").doesNotExist())
                .andExpect(jsonPath("$.recipe.title").value("Apfelkuchen"))
                .andExpect(jsonPath("$.recipe.servings").value(4))
                .andExpect(jsonPath("$.recipe.neededIngredients.length()").value(3))
                .andExpect(jsonPath("$.recipe.preparationSteps.length()").value(2));

        assertEquals(recipesBefore, recipeRepository.count());
    }

    @Test
    void aRecipeWebsiteIsSaved() throws Exception {
        when(recipeScraperServiceProxy.scrapeRecipe(any())).thenReturn(new Gson().toJson(new ScrapedPage(
                "Apfelkuchen", "4 servings", "Alles verrühren.", List.of("200 g Mehl"))));

        importing("Apfelkuchen - https://recipes.example.com/apple-pie")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.recipe.id").isNumber())
                .andExpect(jsonPath("$.recipe.title").value("Apfelkuchen"));
    }

    @Test
    void aChromeShareLinkIsImportedFromThePageBehindIt() throws Exception {
        var page = "https://recipes.example.com/apple-pie";
        doReturn(page).when(shareLinks).resolve("https://share.google/AbC123xyz");
        when(recipeScraperServiceProxy.scrapeRecipe(page)).thenReturn(new Gson().toJson(new ScrapedPage(
                "Apfelkuchen", "4 servings", "Alles verrühren.", List.of("200 g Mehl"))));

        importing("Apfelkuchen\nhttps://share.google/AbC123xyz")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.recipe.recipeSource").value(page));
        verify(recipeScraperServiceProxy).scrapeRecipe(page);
    }

    @Test
    void aWebsiteTheScraperCannotReadIsNotSupported() throws Exception {
        when(recipeScraperServiceProxy.scrapeRecipe(any())).thenThrow(new ImportNotSupportedException());

        importing("https://doesnotmatter.com/")
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_SUPPORTED"));
    }

    /** It never reaches an importer, and it must not be a server error. */
    @Test
    void aLinkWithoutAHostIsInvalid() throws Exception {
        importing("https://my_recipes/pie")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_URL_INVALID"));
    }

    @Test
    void anInstagramLinkComesBackAsADraft() throws Exception {
        when(instagramImporter.needsReview()).thenReturn(true);
        when(instagramImporter.importRecipe(any(), any())).thenReturn(Recipe.builder()
                .owner(cook).title("Big Mac Tacos").servings(1).preparationTime(0L).totalTime(0L).build());

        importing("https://www.instagram.com/reel/C0de/?igsh=abc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.recipe.title").value("Big Mac Tacos"));
    }

    @Test
    void aTextWithoutARecipeOffersTheLinkItPointsTo() throws Exception {
        importing("Neuer Apfelkuchen ist online!\nDas Rezept findest du auf meinem Blog: https://blog.example.com/pie\n#backen")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMPORT_NO_RECIPE"))
                .andExpect(jsonPath("$.link").value("https://blog.example.com/pie"));
    }

    @Test
    void aTextWithoutARecipeOrLinkCarriesNoLink() throws Exception {
        importing("Was für ein schöner Tag am See!\nBis morgen ihr Lieben")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMPORT_NO_RECIPE"))
                .andExpect(jsonPath("$.link").doesNotExist());
    }

    @Test
    void anEmptyOrOverlongInputIsRejected() throws Exception {
        importing(" ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        importing("a".repeat(10_001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions importing(String input) throws Exception {
        return mockMvc.perform(post("/api/v1/recipes/import").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(new Input(input))));
    }

    private record Input(String input) {
    }

    private record ScrapedPage(String title, String yields, String instructions, List<String> ingredients) {
    }
}
