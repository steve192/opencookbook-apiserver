package com.sterul.opencookbookapiserver.integration.cronjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sterul.opencookbookapiserver.cronjobs.IngredientDeletionJob;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.integration.IntegrationTest;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

@SpringBootTest
@ActiveProfiles("integration-test")
class IngredientDeletionJobTest extends IntegrationTest {

    @Autowired
    private IngredientDeletionJob cut;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CookpalUser owner;

    @BeforeEach
    void setup() {
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
        owner = userRepository.findByEmailAddress("deletion-job@example.com");
        if (owner == null) {
            owner = new CookpalUser();
            owner.setEmailAddress("deletion-job@example.com");
            owner = userRepository.save(owner);
        }
    }

    @Test
    void onlyOldIngredientsNoRecipeUsesAreDeleted() {
        var usedOld = ingredient("used and old", 100);
        ingredient("unused and old", 100);
        var unusedNew = ingredient("unused and new", 0);
        recipeRepository.save(Recipe.builder().title("Uses one").owner(owner).servings(1)
                .neededIngredients(new ArrayList<>(List.of(IngredientNeed.builder().ingredient(usedOld).build())))
                .build());

        cut.deleteUnlinkedIngredients();

        var remaining = ingredientRepository.findAll().stream().map(Ingredient::getName).sorted().toList();
        assertEquals(List.of(unusedNew.getName(), usedOld.getName()), remaining);
    }

    @Test
    void anAvoidedIngredientIsNotSweptAwayFromUnderTheProfile() {
        var peanuts = ingredient("Erdnuss", 100);
        planningProfileAvoiding(peanuts);

        cut.deleteUnlinkedIngredients();

        // No recipe of yours contains it - that is *why* you avoided it. Counting only recipe
        // lines deleted exactly the ingredients a cook cared most about, and the cascading
        // foreign key then emptied the allergy out of the profile without a word.
        assertEquals(List.of("Erdnuss"),
                ingredientRepository.findAll().stream().map(Ingredient::getName).toList());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM planning_profile_avoided", Long.class));
    }

    @Test
    void aPantryIngredientIsNotSweptAwayEither() {
        var mince = ingredient("Hackfleisch", 100);
        var profileId = planningProfile();
        jdbcTemplate.update("INSERT INTO planning_profile_pantry (profile_id, ingredient_id, amount, unit)"
                + " VALUES (?, ?, 500, 'g')", profileId, mince.getId());

        cut.deleteUnlinkedIngredients();

        assertEquals(List.of("Hackfleisch"),
                ingredientRepository.findAll().stream().map(Ingredient::getName).toList());
    }

    private void planningProfileAvoiding(Ingredient ingredient) {
        jdbcTemplate.update("INSERT INTO planning_profile_avoided (profile_id, ingredient_id) VALUES (?, ?)",
                planningProfile(), ingredient.getId());
    }

    private Long planningProfile() {
        var id = jdbcTemplate.queryForObject("SELECT nextval('planning_profile_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO planning_profile (id, owner_user_id, name, household_size,"
                + " cooldown_weeks, leftovers_allowed, spread_variety, include_household_recipes, default_profile)"
                + " VALUES (?, ?, 'Normale Woche', 2, 2, true, true, false, true)", id, owner.getUserId());
        return id;
    }

    private Ingredient ingredient(String name, int daysOld) {
        var saved = ingredientRepository.save(Ingredient.builder().name(name).owner(owner).build());
        jdbcTemplate.update("UPDATE ingredient SET created_on = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)), saved.getId());
        return saved;
    }
}
