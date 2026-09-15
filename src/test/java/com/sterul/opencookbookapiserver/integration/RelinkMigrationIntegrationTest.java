package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkProposal;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkRun;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.linking.NameRuleService;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;
import com.sterul.opencookbookapiserver.services.nutrition.relinking.RelinkService;

/** Relink runs and name rules on the migrated schema, including its cascades. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "opencookbook.nutrition.enabled=true"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class RelinkMigrationIntegrationTest {

    // V8__.sql carries an "OWNER to cookpal", so the chain only applies as that role.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> migratedDatabase = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    /** No background import of the shipped dataset. */
    @MockitoBean
    private NutritionDatasetImporter backgroundImporter;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private RelinkService relinkService;
    @Autowired
    private NameRuleService nameRules;

    private CatalogueFood sugar;
    private CookpalUser cook;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM ingredient_relink_run");
        jdbc.update("DELETE FROM catalogue_name_rule");
        jdbc.update("DELETE FROM ingredient");
        jdbc.update("DELETE FROM catalogue_food_name");
        jdbc.update("DELETE FROM catalogue_food");
        sugar = foodRepository.save(CatalogueFood.builder()
                .catalogueKey("custom-sugar")
                .origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(400f).build())
                .names(new ArrayList<>(List.of(CatalogueFoodName.builder().languageIsoCode("de").name("Zucker").display(true)
                        .origin(CatalogueFoodName.Origin.ADMIN).build())))
                .build());
        indexUpdater.rebuild();
        cook = userRepository.findByEmailAddress("relink-migration@example.com");
        if (cook == null) {
            cook = new CookpalUser();
            cook.setEmailAddress("relink-migration@example.com");
            cook.setActivated(true);
            cook.setLanguage("de");
            cook = userRepository.save(cook);
        }
        ingredientRepository.save(Ingredient.builder().name("Zucker").owner(cook).build());
    }

    @Test
    void aRunIsAppliedAndRevertedThroughTheMigratedSchema() throws Exception {
        var run = runWithEveryProposalAccepted();

        relinkService.apply(run.getId());
        assertEquals(sugar.getId(), jdbc.queryForObject("SELECT catalogue_food_id FROM ingredient", Long.class));
        assertEquals(run.getId(), jdbc.queryForObject("SELECT link_run_id FROM ingredient", Long.class));
        assertEquals(true, jdbc.queryForObject("SELECT applied FROM ingredient_relink_member", Boolean.class));

        relinkService.revert(run.getId());
        assertNull(jdbc.queryForObject("SELECT catalogue_food_id FROM ingredient", Long.class));
        assertEquals("REVERTED", jdbc.queryForObject("SELECT status FROM ingredient_relink_run", String.class));
    }

    @Test
    void deletingARunTakesItsProposalsAlongAndLeavesTheLinksItMade() throws Exception {
        var run = runWithEveryProposalAccepted();
        relinkService.apply(run.getId());

        jdbc.update("DELETE FROM ingredient_relink_run");

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredient_relink_member", Integer.class));
        assertNull(jdbc.queryForObject("SELECT link_run_id FROM ingredient", Long.class));
        assertEquals(sugar.getId(), jdbc.queryForObject("SELECT catalogue_food_id FROM ingredient", Long.class));
    }

    @Test
    void aRuleGoesWithItsFood() throws Exception {
        nameRules.neverLinkTo("Zucker", sugar, cook);
        nameRules.notAFood("Zahnstocher", cook);

        jdbc.update("DELETE FROM catalogue_food_name");
        jdbc.update("DELETE FROM catalogue_food");

        assertEquals(List.of("NOT_A_FOOD"), jdbc.queryForList("SELECT kind FROM catalogue_name_rule", String.class));
    }

    private IngredientRelinkRun runWithEveryProposalAccepted() throws Exception {
        var run = relinkService.preview(new RelinkService.Scope(IngredientRelinkRun.Scope.NEVER_MATCHED_OR_UNLINKED, null), cook);
        var proposals = relinkService.getProposals(run.getId());
        assertEquals(1, proposals.size());
        relinkService.decide(run.getId(), proposals.stream().map(IngredientRelinkProposal::getId).toList(),
                IngredientRelinkProposal.Decision.ACCEPTED, false, cook);
        return run;
    }
}
