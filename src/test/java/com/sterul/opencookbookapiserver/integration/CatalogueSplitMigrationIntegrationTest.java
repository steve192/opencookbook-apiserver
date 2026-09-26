package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V17 on a version 16 database holding every case the old model allowed. */
@Testcontainers
class CatalogueSplitMigrationIntegrationTest {

    private static final MigrationVersion BEFORE_THE_SPLIT = MigrationVersion.fromVersion("16");

    // V8__.sql carries an "OWNER to cookpal", so the chain only applies as that role.
    @Container
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToVersion16() {
        flyway(BEFORE_THE_SPLIT).clean();
        flyway(BEFORE_THE_SPLIT).migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    }

    @Test
    void publicIngredientsBecomeLegacyCatalogueFoodsWithTheirNamesAndNutrients() {
        var potato = publicIngredient("Kartoffel", 77f);
        alternativeName(potato, "de", "Erdapfel");
        alternativeName(potato, "en", "Potato");
        var carrot = publicIngredient("Möhre", 36f);
        alternativeName(carrot, "de", "Karotte");
        // Already the older food's name, whatever the capitalisation: the older food keeps it.
        alternativeName(carrot, "de", "erdapfel");

        migrate();

        var foods = jdbc.queryForList("SELECT catalogue_key, origin, energy_kcal FROM catalogue_food ORDER BY id");
        assertEquals(List.of(
                Map.of("catalogue_key", "legacy-" + potato, "origin", "CUSTOM", "energy_kcal", 77f),
                Map.of("catalogue_key", "legacy-" + carrot, "origin", "CUSTOM", "energy_kcal", 36f)), foods);
        assertEquals(List.of("de Kartoffel display", "de Erdapfel", "en Potato"), namesOf("legacy-" + potato));
        assertEquals(List.of("de Möhre display", "de Karotte"), namesOf("legacy-" + carrot));
    }

    @Test
    void anAliasBecomesALinkToTheFoodItsPublicIngredientBecame() {
        var owner = user("alias@example.com");
        var potato = publicIngredient("Kartoffel", 77f);
        var own = privateIngredient("Kartoffeln", owner, potato);
        var unlinked = privateIngredient("Salz", owner, null);

        migrate();

        assertEquals(Map.of("catalogue_key", "legacy-" + potato, "link_source", "AUTO", "link_matcher_version", 0),
                linkOf(own));
        assertEquals(null, jdbc.queryForObject("SELECT catalogue_food_id FROM ingredient WHERE id = ?", Long.class, unlinked));
    }

    @Test
    void aRecipeLineOnAPublicIngredientMovesToAnIngredientOfTheRecipesOwner() {
        var owner = user("lines@example.com");
        var carrot = publicIngredient("Möhre", 36f);
        var line = recipeLine(recipe(owner), carrot);

        migrate();

        var ingredient = jdbc.queryForMap("SELECT ingredient.id, ingredient.name, ingredient.owner_user_id FROM ingredient_need need "
                + "JOIN ingredient ON ingredient.id = need.ingredient_id WHERE need.id = ?", line);
        assertEquals("Möhre", ingredient.get("name"));
        assertEquals(owner, ingredient.get("owner_user_id"));
        assertEquals("legacy-" + carrot, linkOf((Long) ingredient.get("id")).get("catalogue_key"));
    }

    @Test
    void theOwnersIngredientOfThatNameIsUsedAndLinkedWhenItWasNotYet() {
        var owner = user("own-name@example.com");
        var carrot = publicIngredient("Möhre", 36f);
        var own = privateIngredient("Möhre", owner, null);
        var line = recipeLine(recipe(owner), carrot);

        migrate();

        assertEquals(own, ingredientOfLine(line));
        assertEquals("legacy-" + carrot, linkOf(own).get("catalogue_key"));
    }

    @Test
    void anOwnersIngredientsOfOneNameBecomeOneKeepingTheLinkedOne() {
        var owner = user("duplicates@example.com");
        // Named unlike the public ingredient, so only the alias links one of them.
        var potato = publicIngredient("Kartoffel", 77f);
        var unlinked = privateIngredient("Kartoffeln", owner, null);
        var linked = privateIngredient("Kartoffeln", owner, potato);
        var recipe = recipe(owner);
        var lineOnUnlinked = recipeLine(recipe, unlinked);
        var lineOnLinked = recipeLine(recipe, linked);

        migrate();

        assertEquals(List.of(linked), jdbc.queryForList("SELECT id FROM ingredient WHERE owner_user_id = ?", Long.class, owner));
        assertEquals(linked, ingredientOfLine(lineOnUnlinked));
        assertEquals(linked, ingredientOfLine(lineOnLinked));
    }

    @Test
    void theSameNameOfDifferentOwnersStaysTwoIngredients() {
        var potato = publicIngredient("Kartoffel", 77f);
        var first = user("first@example.com");
        var second = user("second@example.com");
        recipeLine(recipe(first), potato);
        recipeLine(recipe(second), potato);

        migrate();

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ingredient WHERE name = 'Kartoffel'", Integer.class));
    }

    @Test
    void aLineNoRecipeOwnsIsDropped() {
        var potato = publicIngredient("Kartoffel", 77f);
        var orphan = need(potato);

        migrate();

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredient_need WHERE id = ?", Integer.class, orphan));
    }

    @Test
    void aLineOnSomebodyElsesIngredientMovesToAnIngredientOfTheRecipesOwner() {
        var author = user("author@example.com");
        var reader = user("reader@example.com");
        var potato = publicIngredient("Kartoffel", 77f);
        var authorsPotato = privateIngredient("Kartoffel", author, potato);
        var readersOnion = privateIngredient("Zwiebel", reader, null);
        var authorsOnion = privateIngredient("Zwiebel", author, null);
        var readersRecipe = recipe(reader);
        var potatoLine = recipeLine(readersRecipe, authorsPotato);
        var onionLine = recipeLine(readersRecipe, authorsOnion);

        migrate();

        var readersPotato = jdbc.queryForMap("SELECT id, catalogue_food_id, link_source FROM ingredient WHERE owner_user_id = ? AND name = 'Kartoffel'",
                reader);
        assertEquals(readersPotato.get("id"), jdbc.queryForObject("SELECT ingredient_id FROM ingredient_need WHERE id = ?", Long.class, potatoLine));
        assertEquals(jdbc.queryForObject("SELECT id FROM catalogue_food WHERE catalogue_key = ?", Long.class, "legacy-" + potato),
                readersPotato.get("catalogue_food_id"));
        assertEquals("AUTO", readersPotato.get("link_source"));
        assertEquals(readersOnion, jdbc.queryForObject("SELECT ingredient_id FROM ingredient_need WHERE id = ?", Long.class, onionLine));
    }

    @Test
    void anIngredientWithoutANameIsDroppedWithItsRecipeLines() {
        var owner = user("blank@example.com");
        var recipe = recipe(owner);
        var blank = privateIngredient(" ", owner, null);
        var blankLine = recipeLine(recipe, blank);
        var blankPublic = publicIngredient("", 0f);
        alternativeName(blankPublic, "", "");
        var blankPublicLine = recipeLine(recipe, blankPublic);
        var salt = privateIngredient("Salz", owner, null);
        var saltLine = recipeLine(recipe, salt);

        migrate();

        assertEquals(List.of(saltLine), jdbc.queryForList(
                "SELECT id FROM ingredient_need WHERE recipe_id = ?", Long.class, recipe));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredient_need WHERE id IN (?, ?)", Integer.class,
                blankLine, blankPublicLine));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredient WHERE id IN (?, ?)", Integer.class, blank, blankPublic));
    }

    @Test
    void nothingOfTheOldModelIsLeft() {
        var owner = user("leftovers@example.com");
        var potato = publicIngredient("Kartoffel", 77f);
        alternativeName(potato, "en", "Potato");
        privateIngredient("Kartoffel", owner, potato);

        migrate();

        assertEquals(List.of(), jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ingredient'
                  AND column_name IN ('is_public_ingredient', 'alias_for_id', 'nutrients_energy', 'nutrients_fat',
                                      'nutrients_saturated_fat', 'nutrients_carbohydrates', 'nutrients_sugar',
                                      'nutrients_protein', 'nutrients_salt')
                UNION ALL
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'catalogue_food' AND column_name = 'legacy_ingredient_id'
                UNION ALL
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'ingredient_alternative_names'
                UNION ALL
                SELECT sequence_name FROM information_schema.sequences
                WHERE sequence_schema = 'public' AND sequence_name = 'ingredient_alternativenames_seq'
                """, String.class));
        assertEquals(List.of("NO", "NO"), jdbc.queryForList("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ingredient' AND column_name IN ('name', 'owner_user_id')
                """, String.class));
    }

    @Test
    void anOwnerCannotHaveTwoIngredientsOfOneNameAfterwards() {
        var owner = user("unique@example.com");
        privateIngredient("Salz", owner, null);

        migrate();

        assertThrows(Exception.class, () -> privateIngredient("Salz", owner));
    }

    @Test
    void aPublicIngredientWithAnOwnerStopsTheMigrationAndLeavesVersion16Untouched() {
        var owner = user("guard@example.com");
        var odd = publicIngredient("Kartoffel", 77f);
        jdbc.update("UPDATE ingredient SET owner_user_id = ? WHERE id = ?", owner, odd);

        var failure = assertThrows(FlywayException.class, this::migrate);

        assertTrue(failure.getMessage().contains("public ingredients with an owner exist"), failure.getMessage());
        assertEquals("16", flyway(MigrationVersion.LATEST).info().current().getVersion().getVersion());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM ingredient WHERE is_public_ingredient", Integer.class));
    }

    private Flyway flyway(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .cleanDisabled(false)
                .load();
    }

    private void migrate() {
        flyway(MigrationVersion.LATEST).migrate();
    }

    private long user(String emailAddress) {
        var id = nextval("cookpal_user_seq");
        jdbc.update("INSERT INTO cookpal_user (user_id, email_address, activated) VALUES (?, ?, true)", id, emailAddress);
        return id;
    }

    private long publicIngredient(String name, float energy) {
        var id = nextval("ingredient_seq");
        jdbc.update("INSERT INTO ingredient (id, is_public_ingredient, name, nutrients_energy) VALUES (?, true, ?, ?)", id, name, energy);
        return id;
    }

    private long privateIngredient(String name, long owner, Long aliasFor) {
        var id = nextval("ingredient_seq");
        jdbc.update("INSERT INTO ingredient (id, is_public_ingredient, name, owner_user_id, alias_for_id) VALUES (?, false, ?, ?, ?)",
                id, name, owner, aliasFor);
        return id;
    }

    /** An ingredient as the application writes it after the split. */
    private void privateIngredient(String name, long owner) {
        jdbc.update("INSERT INTO ingredient (id, name, owner_user_id) VALUES (nextval('ingredient_seq'), ?, ?)", name, owner);
    }

    private void alternativeName(long ingredient, String language, String name) {
        jdbc.update("INSERT INTO ingredient_alternative_names (id, ingredient_id, language_iso_code, alternative_name) "
                + "VALUES (nextval('ingredient_alternativenames_seq'), ?, ?, ?)", ingredient, language, name);
    }

    private long recipe(long owner) {
        var id = nextval("recipe_seq");
        jdbc.update("INSERT INTO recipe (id, servings, title, owner_user_id) VALUES (?, 2, 'Eintopf', ?)", id, owner);
        return id;
    }

    private long need(long ingredient) {
        var id = nextval("ingredient_need_seq");
        jdbc.update("INSERT INTO ingredient_need (id, amount, unit, ingredient_id) VALUES (?, 1, 'kg', ?)", id, ingredient);
        return id;
    }

    private long recipeLine(long recipe, long ingredient) {
        var need = need(ingredient);
        jdbc.update("INSERT INTO recipe_needed_ingredients (recipe_id, needed_ingredients_id) VALUES (?, ?)", recipe, need);
        return need;
    }

    private long nextval(String sequence) {
        return jdbc.queryForObject("SELECT nextval('" + sequence + "')", Long.class);
    }

    private List<String> namesOf(String catalogueKey) {
        return jdbc.queryForList("""
                SELECT name.language_iso_code || ' ' || name.name || CASE WHEN name.display THEN ' display' ELSE '' END
                FROM catalogue_food_name name JOIN catalogue_food food ON food.id = name.catalogue_food_id
                WHERE food.catalogue_key = ? AND name.origin = 'ADMIN'
                ORDER BY name.display DESC, name.language_iso_code, name.name
                """, String.class, catalogueKey);
    }

    private Map<String, Object> linkOf(long ingredient) {
        return jdbc.queryForMap("SELECT food.catalogue_key, ingredient.link_source, ingredient.link_matcher_version "
                + "FROM ingredient JOIN catalogue_food food ON food.id = ingredient.catalogue_food_id WHERE ingredient.id = ?", ingredient);
    }

    private long ingredientOfLine(long need) {
        return jdbc.queryForObject("SELECT ingredient_id FROM ingredient_need WHERE id = ?", Long.class, need);
    }
}
