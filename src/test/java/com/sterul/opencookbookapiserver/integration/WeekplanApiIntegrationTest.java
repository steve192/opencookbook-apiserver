package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Pins the weekplan wire format. The day is a {@code LocalDate}: it has to keep serializing as
 * plain {@code yyyy-MM-dd} and must come back as the very date it was stored under, independent
 * of the JVM timezone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class WeekplanApiIntegrationTest extends IntegrationTest {

    private static final String USER = "weekplan-api@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long recipeId;
    private Long secondRecipeId;

    @BeforeEach
    void setup() {
        var user = userRepository.findByEmailAddress(USER);
        if (user == null) {
            user = new CookpalUser();
            user.setEmailAddress(USER);
            user.setPasswordHash("irrelevant");
            user.setActivated(true);
            user = userRepository.save(user);
        }
        var recipe = new Recipe();
        recipe.setTitle("Weekplan recipe");
        recipe.setOwner(user);
        recipeId = recipeRepository.save(recipe).getId();

        var secondRecipe = new Recipe();
        secondRecipe.setTitle("Second weekplan recipe");
        secondRecipe.setOwner(user);
        secondRecipeId = recipeRepository.save(secondRecipe).getId();
    }

    private String putDay(String date, String recipesJson) throws Exception {
        return mockMvc.perform(put("/api/v1/weekplan/" + date)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"recipes\": [ " + recipesJson + " ] }"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String normalRecipe(Long id) {
        return "{ \"type\": \"NORMAL_RECIPE\", \"id\": %d }".formatted(id);
    }

    private String simpleRecipe(String title) {
        return simpleRecipe("", title);
    }

    /** A spontaneous meal as the app sends it back: with the id the server gave it. */
    private String simpleRecipe(String id, String title) {
        return "{ \"type\": \"SIMPLE_RECIPE\", \"id\": \"%s\", \"title\": \"%s\" }".formatted(id, title);
    }

    private String storedIdOf(String dayResponse, int index) {
        return JsonPath.read(dayResponse, "$.recipes[" + index + "].id").toString();
    }

    @Test
    @WithMockUser(username = USER)
    void weekplanDayRoundTripsUnderTheDateItWasStoredWith() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/2026-01-05")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "recipes": [
                            { "type": "NORMAL_RECIPE", "id": %d },
                            { "type": "SIMPLE_RECIPE", "id": "", "title": "Leftovers" }
                        ] }
                        """.formatted(recipeId)))
                .andExpect(status().isOk())
                // the date must not drift by a day through the timezone
                .andExpect(jsonPath("$.day").value("2026-01-05"))
                .andExpect(jsonPath("$.recipes.length()").value(2));

        mockMvc.perform(get("/api/v1/weekplan/2026-01-01/to/2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].day").value("2026-01-05"))
                .andExpect(jsonPath("$[0].recipes[?(@.type=='NORMAL_RECIPE')].title").value("Weekplan recipe"))
                .andExpect(jsonPath("$[0].recipes[?(@.type=='SIMPLE_RECIPE')].title").value("Leftovers"));
    }

    /**
     * The meals of a day are shown, printed and reordered in list order, so the order the day
     * was stored with has to be the order it comes back in. Without an order column on the
     * association this is a JPA bag, and a reorder was written but read back arbitrarily.
     */
    @Test
    @WithMockUser(username = USER)
    void mealsComeBackInTheOrderTheyWereStoredIn() throws Exception {
        putDay("2026-02-02", String.join(",",
                simpleRecipe("first"), normalRecipe(recipeId), simpleRecipe("third")));

        mockMvc.perform(get("/api/v1/weekplan/2026-02-01/to/2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.day=='2026-02-02')].recipes[0].title").value("first"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-02')].recipes[1].title").value("Weekplan recipe"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-02')].recipes[2].title").value("third"));
    }

    @Test
    @WithMockUser(username = USER)
    void reorderingTheMealsOfADayIsPersisted() throws Exception {
        putDay("2026-02-09", String.join(",",
                simpleRecipe("starter"), simpleRecipe("main"), simpleRecipe("dessert")));

        // What the app sends after the user moves the last meal to the top
        putDay("2026-02-09", String.join(",",
                simpleRecipe("dessert"), simpleRecipe("starter"), simpleRecipe("main")));

        mockMvc.perform(get("/api/v1/weekplan/2026-02-01/to/2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.day=='2026-02-09')].recipes[0].title").value("dessert"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-09')].recipes[1].title").value("starter"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-09')].recipes[2].title").value("main"));
    }

    /** Reordering must not disturb which recipe an entry points at. */
    @Test
    @WithMockUser(username = USER)
    void reorderingKeepsSavedRecipesAndSpontaneousMealsApart() throws Exception {
        putDay("2026-02-16", String.join(",",
                normalRecipe(recipeId), simpleRecipe("spontaneous"), normalRecipe(secondRecipeId)));

        putDay("2026-02-16", String.join(",",
                normalRecipe(secondRecipeId), normalRecipe(recipeId), simpleRecipe("spontaneous")));

        mockMvc.perform(get("/api/v1/weekplan/2026-02-01/to/2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.day=='2026-02-16')].recipes[0].title").value("Second weekplan recipe"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-16')].recipes[0].type").value("NORMAL_RECIPE"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-16')].recipes[1].title").value("Weekplan recipe"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-16')].recipes[2].title").value("spontaneous"))
                .andExpect(jsonPath("$[?(@.day=='2026-02-16')].recipes[2].type").value("SIMPLE_RECIPE"));
    }

    /**
     * The app sends the whole day back on every change, spontaneous meals included, and those
     * carry the id the server gave them. Building the day's collection while resolving the
     * recipes handed JPA one of those as a detached entity mid flush, which failed the request
     * with "Detached entity passed to persist" - but only when a saved recipe followed a
     * spontaneous meal, because nothing flushes until a recipe is looked up.
     */
    @Test
    @WithMockUser(username = USER)
    void aRecipeCanBeAddedToADayThatAlreadyHoldsSpontaneousMeals() throws Exception {
        var stored = putDay("2026-04-06", String.join(",",
                simpleRecipe("one"), simpleRecipe("two"), simpleRecipe("three")));

        putDay("2026-04-06", String.join(",",
                simpleRecipe(storedIdOf(stored, 0), "one"),
                simpleRecipe(storedIdOf(stored, 1), "two"),
                simpleRecipe(storedIdOf(stored, 2), "three"),
                normalRecipe(recipeId)));

        // Exactly four entries in the order they were sent: the day is replaced wholesale,
        // so the meals it already held must be neither duplicated nor lost.
        mockMvc.perform(get("/api/v1/weekplan/2026-04-01/to/2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.day=='2026-04-06')].recipes.length()").value(4))
                .andExpect(jsonPath("$[?(@.day=='2026-04-06')].recipes[0].title").value("one"))
                .andExpect(jsonPath("$[?(@.day=='2026-04-06')].recipes[1].title").value("two"))
                .andExpect(jsonPath("$[?(@.day=='2026-04-06')].recipes[2].title").value("three"))
                .andExpect(jsonPath("$[?(@.day=='2026-04-06')].recipes[3].title").value("Weekplan recipe"));
    }

    /**
     * The order coming back from the api can be right by accident: a small, freshly written
     * table tends to be returned in insertion order even without an ORDER BY. This asserts
     * that the position is stored, which is what actually makes the order survive.
     */
    @Test
    @WithMockUser(username = USER)
    void thePositionOfEachMealIsStored() throws Exception {
        putDay("2026-05-04", String.join(",",
                simpleRecipe("a"), simpleRecipe("b"), simpleRecipe("c")));

        var stored = jdbcTemplate.queryForList("""
                SELECT link.recipe_order, meal.simple_recipe_text
                FROM weekplan_day_recipes link
                JOIN weekplan_day day ON day.id = link.weekplan_day_id
                JOIN weekplan_day_recipe meal ON meal.id = link.recipes_id
                WHERE day.plan_date = DATE '2026-05-04'
                ORDER BY link.recipe_order
                """);

        assertEquals(List.of(0, 1, 2), stored.stream().map(row -> row.get("recipe_order")).toList());
        assertEquals(List.of("a", "b", "c"), stored.stream().map(row -> row.get("simple_recipe_text")).toList());
    }

    @Test
    @WithMockUser(username = USER)
    void weekplanDayOutsideTheRequestedRangeIsNotReturned() throws Exception {
        mockMvc.perform(put("/api/v1/weekplan/2026-03-09")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"recipes\": [ { \"type\": \"SIMPLE_RECIPE\", \"id\": \"\", \"title\": \"Soup\" } ] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value("2026-03-09"));

        mockMvc.perform(get("/api/v1/weekplan/2026-01-01/to/2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.day=='2026-03-09')]").isEmpty());
    }
}
