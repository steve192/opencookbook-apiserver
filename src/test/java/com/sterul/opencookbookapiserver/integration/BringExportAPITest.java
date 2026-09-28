package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingStaple;
import com.sterul.opencookbookapiserver.repositories.BringExportRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingStapleRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** The page Bring fetches without a token, and the two ways of filling it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class BringExportAPITest extends IntegrationTestBase {

    private static final String COOK = "bring-cook@example.invalid";
    private static final String STRANGER = "bring-stranger@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private BringExportRepository exportRepository;
    @Autowired
    private ShoppingStapleRepository stapleRepository;

    private Long recipeId;

    @BeforeEach
    void setup() {
        exportRepository.deleteAll();
        stapleRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
        var cook = TestAccounts.ensure(userRepository, COOK);
        TestAccounts.ensure(userRepository, STRANGER);
        var apple = ingredientRepository.save(Ingredient.builder().name("Apple").owner(cook).build());
        var trap = ingredientRepository.save(Ingredient.builder().name("<img src=x onerror=alert(1)>").owner(cook).build());
        recipeId = recipeRepository.save(Recipe.builder()
                .title("Apfel & Co").owner(cook).servings(4)
                .images(new ArrayList<>()).preparationSteps(new ArrayList<>()).recipeGroups(new ArrayList<>())
                .neededIngredients(new ArrayList<>(List.of(
                        IngredientNeed.builder().amount(10f).unit("Pcs").ingredient(apple).build(),
                        IngredientNeed.builder().amount(1f).unit("g").ingredient(trap).build())))
                .build()).getId();
    }

    @Test
    void aRecipeBecomesAPageBringCanRead() throws Exception {
        var exportId = exportOfRecipe(COOK);

        mockMvc.perform(get("/api/v1/bringexport").param("exportId", exportId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span itemProp='yield'>4</span>")))
                .andExpect(content().string(containsString("<h1 itemProp='name'>Apfel &amp; Co</h1>")))
                .andExpect(content().string(containsString("<li itemProp='ingredients'>10 Pcs Apple</li>")));
    }

    @Test
    void whatPeopleTypedIsShownAndNeverRun() throws Exception {
        var exportId = exportOfRecipe(COOK);

        mockMvc.perform(get("/api/v1/bringexport").param("exportId", exportId))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(content().string(not(containsString("<img src=x"))))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")));
    }

    @Test
    void aRecipeWithALongTitleCanBeExported() throws Exception {
        var recipe = recipeRepository.findById(recipeId).orElseThrow();
        recipe.setTitle("Omas ".repeat(40).strip());
        recipeRepository.save(recipe);

        mockMvc.perform(get("/api/v1/bringexport").param("exportId", exportOfRecipe(COOK)))
                .andExpect(content().string(containsString(recipe.getTitle())));
    }

    @Test
    void aRecipeSomebodyCannotReadIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/bringexport").with(asUser(STRANGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipeId\": " + recipeId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void finishedLinesGoToBringUnderTheirOwnTitle() throws Exception {
        var body = mockMvc.perform(post("/api/v1/bringexport/lines").with(asUser(COOK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linesRequest("\"Brot\", \"<b>Butter</b>\"", "")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/bringexport").param("exportId", (String) JsonPath.read(body, "$.exportId")))
                .andExpect(content().string(containsString("<h1 itemProp='name'>Woche 40</h1>")))
                .andExpect(content().string(containsString("<li itemProp='ingredients'>&lt;b&gt;Butter&lt;/b&gt;</li>")));
    }

    @Test
    void aLineLeftOutThreeTimesInARowBecomesAStaple() throws Exception {
        var saltLeftOut = "{\"name\": \"Salz\", \"ticked\": false}";
        for (var import_ = 0; import_ < 3; import_++) {
            mockMvc.perform(post("/api/v1/bringexport/lines").with(asUser(COOK))
                            .contentType(MediaType.APPLICATION_JSON).content(linesRequest("\"Brot\"", saltLeftOut)))
                    .andExpect(status().isOk());
        }

        var staples = stapleRepository.findAllByUserAndStapleTrueOrderByName(userRepository.findByEmailAddress(COOK));
        assertEquals(List.of("Salz"), staples.stream().map(ShoppingStaple::getName).toList());
    }

    @Test
    void creatingAnExportNeedsAToken() throws Exception {
        mockMvc.perform(post("/api/v1/bringexport/lines")
                        .contentType(MediaType.APPLICATION_JSON).content(linesRequest("\"Brot\"", "")))
                .andExpect(status().isUnauthorized());
    }

    private String exportOfRecipe(String user) throws Exception {
        var body = mockMvc.perform(post("/api/v1/bringexport").with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipeId\": " + recipeId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.exportId");
    }

    private static String linesRequest(String lines, String shown) {
        return "{\"title\": \"Woche 40\", \"servings\": 2, \"lines\": [" + lines + "], \"shown\": [" + shown + "]}";
    }

    private static RequestPostProcessor asUser(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name);
    }
}
