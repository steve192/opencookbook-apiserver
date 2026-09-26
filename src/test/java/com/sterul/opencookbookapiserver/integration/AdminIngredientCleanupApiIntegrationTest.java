package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminIngredientCleanupApiIntegrationTest extends IntegrationTestBase {

    private static final String OPERATOR = "cleanup-operator@example.com";
    private static final String COOK = "cleanup-cook@example.com";
    private static final String RECIPE = """
            { "title": "Zimtkuchen", "servings": 4,
              "neededIngredients": [
                { "amount": 1, "unit": "", "ingredient": { "name": "Prise Zimt" } },
                { "amount": 2, "unit": "", "ingredient": { "name": "200g Schmelzkäse" } },
                { "amount": 1, "unit": "EL", "ingredient": { "name": "Zucker" } },
                { "amount": 1, "unit": "", "ingredient": { "name": "EL Zucker" } },
                { "amount": 3, "unit": "", "ingredient": { "name": "Eier" } } ] }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;

    private long recipeId;

    @BeforeEach
    void recipe() throws Exception {
        shareRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
        TestAccounts.ensure(userRepository, OPERATOR);
        TestAccounts.ensure(userRepository, COOK);
        var body = mockMvc.perform(post("/api/v1/recipes").with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content(RECIPE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        recipeId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void thePreviewProposesWhatCleaningWouldChange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingredients/name-cleanup").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].proposedName").value(containsInAnyOrder("Zimt", "Schmelzkäse", "Zucker")))
                .andExpect(jsonPath("$[?(@.name=='200g Schmelzkäse')].lines[0].amount").value(2.0))
                .andExpect(jsonPath("$[?(@.name=='200g Schmelzkäse')].lines[0].newAmount").value(200.0))
                .andExpect(jsonPath("$[?(@.name=='200g Schmelzkäse')].lines[0].newUnit").value("g"))
                .andExpect(jsonPath("$[?(@.name=='EL Zucker')].mergesIntoIngredientId").value(ingredientId("Zucker").intValue()));
    }

    @Test
    void cleaningMovesAmountAndUnitIntoTheLinesAndMergesWhatTheOwnerHasAlready() throws Exception {
        var decisions = decisions(Map.of("Prise Zimt", "Ceylon-Zimt"));

        mockMvc.perform(apply(decisions))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renamed").value(2))
                .andExpect(jsonPath("$.merged").value(1))
                .andExpect(jsonPath("$.linesChanged").value(3))
                .andExpect(jsonPath("$.skipped").value(0));

        var zucker = ingredientId("Zucker");
        mockMvc.perform(get("/api/v1/recipes/" + recipeId).with(user(COOK)))
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.name").value("Ceylon-Zimt"))
                .andExpect(jsonPath("$.neededIngredients[0].amount").value(1.0))
                .andExpect(jsonPath("$.neededIngredients[0].unit").value("Prise"))
                .andExpect(jsonPath("$.neededIngredients[1].ingredient.name").value("Schmelzkäse"))
                .andExpect(jsonPath("$.neededIngredients[1].amount").value(200.0))
                .andExpect(jsonPath("$.neededIngredients[1].unit").value("g"))
                .andExpect(jsonPath("$.neededIngredients[3].ingredient.id").value(zucker.intValue()))
                .andExpect(jsonPath("$.neededIngredients[3].unit").value("EL"))
                .andExpect(jsonPath("$.neededIngredients[4].ingredient.name").value("Eier"));
        assertThat(ingredientRepository.findByNameAndOwner("EL Zucker", cook())).isEmpty();
    }

    @Test
    void anIngredientChangedSinceThePreviewIsSkipped() throws Exception {
        var decisions = decisions(Map.of());
        var zimt = ingredientRepository.findByNameAndOwner("Prise Zimt", cook()).orElseThrow();
        zimt.setAdditionalInfo("gemahlen");
        ingredientRepository.saveAndFlush(zimt);

        mockMvc.perform(apply(decisions))
                .andExpect(jsonPath("$.renamed").value(1))
                .andExpect(jsonPath("$.merged").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void cooksCannotCleanNames() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingredients/name-cleanup").with(user(COOK)))
                .andExpect(status().isForbidden());
    }

    /** Every proposal of the preview, with the proposed name unless corrected. */
    private String decisions(Map<String, String> corrections) throws Exception {
        var preview = mockMvc.perform(get("/api/v1/admin/ingredients/name-cleanup").with(operator()))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> proposals = JsonPath.read(preview, "$[*]");
        var decisions = proposals.stream()
                .map(proposal -> "{\"ingredientId\": %s, \"name\": \"%s\", \"lastChange\": \"%s\"}".formatted(proposal.get("ingredientId"),
                        corrections.getOrDefault((String) proposal.get("name"), (String) proposal.get("proposedName")),
                        proposal.get("lastChange")))
                .toList();
        return "{\"decisions\": [" + String.join(", ", decisions) + "]}";
    }

    private org.springframework.test.web.servlet.RequestBuilder apply(String decisions) {
        return post("/api/v1/admin/ingredients/name-cleanup/apply").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content(decisions);
    }

    private Long ingredientId(String name) {
        return ingredientRepository.findByNameAndOwner(name, cook()).orElseThrow().getId();
    }

    private CookpalUser cook() {
        return userRepository.findByEmailAddress(COOK);
    }

    private static RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
    }
}
