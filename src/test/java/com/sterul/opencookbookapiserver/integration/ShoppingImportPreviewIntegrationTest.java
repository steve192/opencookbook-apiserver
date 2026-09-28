package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingStaple;
import com.sterul.opencookbookapiserver.repositories.PlanningProfileRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingStapleRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;

/** What an import sheet is offered: the week's meals, servings to shop for, and lines the app can add up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShoppingImportPreviewIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "preview-cook@example.invalid";
    private static final String STRANGER = "preview-stranger@example.invalid";
    private static final String MONDAY = "2026-10-05";
    private static final String RECIPE = """
            { "title": "Pfannkuchen", "servings": 4,
              "neededIngredients": [
                { "amount": 500, "unit": "g", "ingredient": { "name": "Mehl" } },
                { "amount": 1, "unit": "kg", "ingredient": { "name": "Kartoffeln" } },
                { "amount": 2, "unit": "EL", "ingredient": { "name": "Öl" } },
                { "amount": 1, "unit": "Prise", "ingredient": { "name": "Salz" } } ] }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private WeekplanDayRepository weekplanDayRepository;
    @Autowired
    private PlanningProfileRepository profileRepository;
    @Autowired
    private ShoppingStapleRepository stapleRepository;

    private long recipeId;

    @BeforeEach
    void setup() throws Exception {
        profileRepository.deleteAll();
        weekplanDayRepository.deleteAll();
        stapleRepository.deleteAll();
        recipeRepository.deleteAll();
        var cook = TestAccounts.ensure(userRepository, COOK);
        TestAccounts.ensure(userRepository, STRANGER);
        stapleRepository.save(ShoppingStaple.builder().user(cook).nameKey("salz").name("Salz").untickedStreak(3)
                .staple(true).build());
        var body = mockMvc.perform(post("/api/v1/recipes").with(user(COOK))
                        .contentType(MediaType.APPLICATION_JSON).content(RECIPE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        recipeId = ((Number) JsonPath.read(body, "$.id")).longValue();
        // A leftover the week generator planned is the same recipe planned a second time.
        mockMvc.perform(put("/api/v1/weekplan/" + MONDAY).with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipes\":[{\"type\":\"NORMAL_RECIPE\",\"id\":" + recipeId + "},"
                                + "{\"type\":\"NORMAL_RECIPE\",\"id\":" + recipeId + "},"
                                + "{\"type\":\"SIMPLE_RECIPE\",\"title\":\"Pizzaabend\"}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void everyPlannedRecipeIsShoppedForTheHouseholdSize() throws Exception {
        var profile = PlanningProfile.builder().name("Normal").defaultProfile(true).householdSize(3).build();
        PlanScope.of(userRepository.findByEmailAddress(COOK)).assignTo(profile);
        profileRepository.save(profile);

        week().andExpect(jsonPath("$.meals", hasSize(3)))
                .andExpect(jsonPath("$.meals[0].recipeServings").value(4))
                .andExpect(jsonPath("$.meals[0].defaultServings").value(3))
                .andExpect(jsonPath("$.meals[1].defaultServings").value(3));
    }

    @Test
    void withoutAProfileARecipeIsShoppedForAsWritten() throws Exception {
        week().andExpect(jsonPath("$.meals[0].defaultServings").value(4));
    }

    @Test
    void metricAmountsAddUpInGramsAndSpoonsStaySpoons() throws Exception {
        week().andExpect(jsonPath("$.meals[0].lines[0].name").value("Mehl"))
                .andExpect(jsonPath("$.meals[0].lines[0].mergeUnit").value("g"))
                .andExpect(jsonPath("$.meals[0].lines[0].mergeFactor").value(1.0))
                .andExpect(jsonPath("$.meals[0].lines[1].mergeUnit").value("g"))
                .andExpect(jsonPath("$.meals[0].lines[1].mergeFactor").value(1000.0))
                .andExpect(jsonPath("$.meals[0].lines[2].mergeUnit").value("tablespoon"));
    }

    @Test
    void whatThePersonKeepsAtHomeIsMarked() throws Exception {
        week().andExpect(jsonPath("$.meals[0].lines[3].staple").value(true))
                .andExpect(jsonPath("$.meals[0].lines[0].staple").value(false));
    }

    @Test
    void aMealWithoutARecipeIsOfferedForItemsTypedInTheSheet() throws Exception {
        week().andExpect(jsonPath("$.meals[2].title").value("Pizzaabend"))
                .andExpect(jsonPath("$.meals[2].spontaneous").value(true))
                .andExpect(jsonPath("$.meals[2].lines", hasSize(0)));
    }

    @Test
    void aRecipeIsPreviewedForWhoeverMayReadIt() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/preview/recipe/" + recipeId).with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].date").doesNotExist())
                .andExpect(jsonPath("$.meals[0].lines", hasSize(4)));
        mockMvc.perform(get("/api/v1/shopping/preview/recipe/" + recipeId).with(user(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRangeLongerThanAMonthIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/preview/week").param("from", MONDAY).param("to", "2026-12-31")
                        .with(user(COOK)))
                .andExpect(status().isBadRequest());
    }

    private ResultActions week() throws Exception {
        return mockMvc.perform(get("/api/v1/shopping/preview/week").param("from", MONDAY).param("to", "2026-10-11")
                        .with(user(COOK)))
                .andExpect(status().isOk());
    }
}
