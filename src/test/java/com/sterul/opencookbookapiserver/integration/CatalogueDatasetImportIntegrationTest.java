package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.NutritionDatasetImport;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.NutritionDatasetImportRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueDatasetApplier;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.DatasetImportLedger;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

/** On the migrated schema, whose case-insensitive name constraint must hold while names move between foods. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "opencookbook.nutrition.enabled=true"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class CatalogueDatasetImportIntegrationTest {

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
    private NutritionDatasetReader reader;
    @Autowired
    private DatasetImportLedger ledger;
    @Autowired
    private CatalogueDatasetApplier applier;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private NutritionDatasetImportRepository importRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private CatalogueMatcher matcher;

    private CookpalUser owner;

    @BeforeEach
    void emptyCatalogue() {
        jdbcTemplate.update("DELETE FROM ingredient");
        jdbcTemplate.update("UPDATE catalogue_food SET variant_of_id = NULL");
        jdbcTemplate.update("DELETE FROM catalogue_food");
        jdbcTemplate.update("DELETE FROM nutrition_dataset_import");
        owner = userRepository.findByEmailAddress("catalogue-import@example.com");
        if (owner == null) {
            var user = new CookpalUser();
            user.setEmailAddress("catalogue-import@example.com");
            user.setActivated(true);
            owner = userRepository.save(user);
        }
    }

    @Test
    void theShippedDatasetIsImportedCompletelyAndOnlyOnce() {
        var importer = new NutritionDatasetImporter(reader, ledger, applier, new SyncTaskExecutor(), events);
        var manifest = reader.manifest();

        importer.importShippedDataset();

        var imported = importRepository.findById(manifest.checksum()).orElseThrow();
        assertEquals(NutritionDatasetImport.Status.DONE, imported.getStatus(), imported.getReport());
        assertEquals(manifest.foods(), foodRepository.findAllByOrigin(CatalogueFood.Origin.DATASET).size());

        importer.importShippedDataset();

        assertEquals(imported.getFinishedAt(), importRepository.findById(manifest.checksum()).orElseThrow().getFinishedAt());
    }

    @Test
    void theMatcherFindsFoodsOfTheImportedCatalogue() {
        new NutritionDatasetImporter(reader, ledger, applier, new SyncTaskExecutor(), events).importShippedDataset();

        indexUpdater.rebuild();

        assertTrue(matcher.isReady());
        assertEquals("bls-K110100", matcher.match("mehligkochende Kartoffeln", "de").orElseThrow().foodKey());
    }

    @Test
    void aNewReleaseUpdatesItsFoodsAndRetiresTheOnesItNoLongerHas() {
        applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Kartoffel", true)),
                food("bls-2", "bls-1", name("de", "Kartoffel gekocht", true)))));

        var report = applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Kartoffel", true), name("en", "Potato", true)))));

        assertTrue(report.contains("retired bls-2 (no longer in the dataset)"), report.toString());
        read(() -> {
            assertEquals(List.of("de Kartoffel", "en Potato"), names("bls-1"));
            assertTrue(foodRepository.findByCatalogueKey("bls-2").orElseThrow().isRetired());
        });
    }

    @Test
    void aNameMayMoveFromOneDatasetFoodToAnotherBetweenReleases() {
        applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Möhre", true), name("de", "Karotte", false)),
                food("bls-2", null, name("de", "Pastinake", true)))));

        applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Möhre", true)),
                food("bls-2", null, name("de", "Pastinake", true), name("de", "karotte", false)))));

        read(() -> {
            assertEquals(List.of("de Möhre"), names("bls-1"));
            assertEquals(List.of("de Pastinake", "de karotte"), names("bls-2"));
        });
    }

    @Test
    void aLegacyFoodSharingANameIsMergedIntoTheDatasetFoodTogetherWithItsLinksAndOtherNames() {
        var legacy = foodRepository.save(CatalogueFood.builder()
                .catalogueKey("legacy-42")
                .origin(CatalogueFood.Origin.CUSTOM)
                .names(new ArrayList<>(List.of(adminName("de", "Kartoffel", true), adminName("de", "Erdapfel", false))))
                .build());
        var ingredient = ingredientRepository.save(Ingredient.builder()
                .name("Kartoffeln").owner(owner).catalogueFood(legacy).linkSource(Ingredient.LinkSource.AUTO).build());

        var report = applier.apply(new NutritionDataset.Catalogue(List.of(food("bls-1", null, name("de", "Kartoffel", true)))));

        assertTrue(report.contains("merged legacy-42 into bls-1 (1 ingredients relinked)"), report.toString());
        read(() -> {
            assertFalse(foodRepository.findByCatalogueKey("legacy-42").isPresent());
            assertEquals("bls-1", ingredientRepository.findById(ingredient.getId()).orElseThrow().getCatalogueFood().getCatalogueKey());
            assertEquals(List.of("de Erdapfel", "de Kartoffel"), names("bls-1"));
        });
    }

    @Test
    void anAdministratorsNameIsKeptUntilAReleaseGivesItToAnotherFood() {
        applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Kartoffel", true)),
                food("bls-2", null, name("de", "Süßkartoffel", true)))));
        transactionTemplate.executeWithoutResult(status -> foodRepository.findByCatalogueKey("bls-1").orElseThrow()
                .getNames().add(adminName("de", "Batate", false)));

        var keeping = applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Kartoffel", true)),
                food("bls-2", null, name("de", "Süßkartoffel", true)))));
        read(() -> assertEquals(List.of("de Batate", "de Kartoffel"), names("bls-1")));
        assertTrue(keeping.stream().noneMatch(line -> line.contains("Batate")), keeping.toString());

        var claiming = applier.apply(new NutritionDataset.Catalogue(List.of(
                food("bls-1", null, name("de", "Kartoffel", true)),
                food("bls-2", null, name("de", "Süßkartoffel", true), name("de", "Batate", false)))));

        assertTrue(claiming.contains("administrator name 'Batate' of bls-1 dropped, the dataset gives it to bls-2"), claiming.toString());
        read(() -> {
            assertEquals(List.of("de Kartoffel"), names("bls-1"));
            assertEquals(List.of("de Batate", "de Süßkartoffel"), names("bls-2"));
        });
    }

    /** Names are loaded lazily, so assertions on them run inside a transaction. */
    private void read(Runnable assertions) {
        transactionTemplate.executeWithoutResult(status -> assertions.run());
    }

    /** A food's names as "language name", sorted. */
    private List<String> names(String catalogueKey) {
        return foodRepository.findByCatalogueKey(catalogueKey).orElseThrow().getNames().stream()
                .map(name -> name.getLanguageIsoCode() + " " + name.getName())
                .sorted()
                .toList();
    }

    private static NutritionDataset.Food food(String key, String variantOf, NutritionDataset.Name... names) {
        var nutrients = new NutritionDataset.Nutrients(77f, 322f, 0.1f, 0f, 15f, 0.7f, 2f, 2f, 0f);
        return new NutritionDataset.Food(key, variantOf, new NutritionDataset.Source("BLS", key.substring(4), key),
                List.of(), false, null, nutrients, List.of(names), List.of());
    }

    private static NutritionDataset.Name name(String language, String name, boolean display) {
        return new NutritionDataset.Name(language, name, display);
    }

    private static CatalogueFoodName adminName(String language, String name, boolean display) {
        return CatalogueFoodName.builder().languageIsoCode(language).name(name).display(display)
                .origin(CatalogueFoodName.Origin.ADMIN).build();
    }
}
