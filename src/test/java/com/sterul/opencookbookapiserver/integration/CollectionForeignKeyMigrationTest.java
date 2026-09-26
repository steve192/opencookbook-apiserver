package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V29 moves three collections out of their join tables and onto the children themselves. Every
 * other migration test starts from an empty database and would pass even if the migration
 * dropped every row on the floor, so this one fills the join tables first and only then runs
 * it. Flyway is driven by hand for that reason: the rows have to exist before V29 does.
 */
@Testcontainers
class CollectionForeignKeyMigrationTest {

    @Container
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    @Test
    void everyCollectionRowArrivesOnItsChildInTheOrderItHad() {
        var dataSource = dataSource();
        migrateTo(dataSource, "28");
        var jdbc = new JdbcTemplate(dataSource);
        seedJoinTables(jdbc);

        migrateTo(dataSource, "29");

        assertEquals(7L, jdbc.queryForObject(
                "SELECT recipe_id FROM ingredient_need WHERE id = 100", Long.class),
                "the ingredient kept the recipe it belonged to");
        assertEquals(7L, jdbc.queryForObject(
                "SELECT recipe_id FROM recipe_image WHERE uuid = 'image-b'", Long.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT image_order FROM recipe_image WHERE uuid = 'image-b'", Integer.class),
                "which image is the title image is decided by this order");
        assertEquals(0, jdbc.queryForObject(
                "SELECT image_order FROM recipe_image WHERE uuid = 'image-a'", Integer.class));
        assertEquals(5L, jdbc.queryForObject(
                "SELECT weekplan_day_id FROM weekplan_day_recipe WHERE id = 'planned-a'", Long.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT recipe_order FROM weekplan_day_recipe WHERE id = 'planned-b'", Integer.class));

        // An image nobody attached keeps its null parent rather than being deleted.
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM recipe_image WHERE uuid = 'image-loose' AND recipe_id IS NULL",
                Integer.class));
    }

    private void seedJoinTables(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO cookpal_user (user_id, email_address, password_hash, activated)"
                + " VALUES (1, 'migration@example.invalid', 'x', true)");
        jdbc.update("INSERT INTO recipe (id, title, servings, owner_user_id) VALUES (7, 'Soup', 2, 1)");
        // The ingredient itself is beside the point here, and its columns have moved twice.
        jdbc.update("INSERT INTO ingredient_need (id, amount, unit) VALUES (100, 1, 'g')");
        jdbc.update("INSERT INTO recipe_needed_ingredients (recipe_id, needed_ingredients_id) VALUES (7, 100)");

        jdbc.update("INSERT INTO recipe_image (uuid, owner_user_id) VALUES ('image-a', 1)");
        jdbc.update("INSERT INTO recipe_image (uuid, owner_user_id) VALUES ('image-b', 1)");
        jdbc.update("INSERT INTO recipe_image (uuid, owner_user_id) VALUES ('image-loose', 1)");
        jdbc.update("INSERT INTO recipe_images (recipe_id, images_uuid, image_order) VALUES (7, 'image-a', 0)");
        jdbc.update("INSERT INTO recipe_images (recipe_id, images_uuid, image_order) VALUES (7, 'image-b', 1)");

        jdbc.update("INSERT INTO weekplan_day (id, plan_date, owner_user_id) VALUES (5, '2026-01-01', 1)");
        jdbc.update("INSERT INTO weekplan_day_recipe (id, is_simple_recipe, recipe_id) VALUES ('planned-a', false, 7)");
        jdbc.update("INSERT INTO weekplan_day_recipe (id, is_simple_recipe, simple_recipe_text)"
                + " VALUES ('planned-b', true, 'Leftovers')");
        jdbc.update("INSERT INTO weekplan_day_recipes (weekplan_day_id, recipes_id, recipe_order)"
                + " VALUES (5, 'planned-a', 0)");
        jdbc.update("INSERT INTO weekplan_day_recipes (weekplan_day_id, recipes_id, recipe_order)"
                + " VALUES (5, 'planned-b', 1)");
    }

    private static void migrateTo(DataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .target(version)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private static DataSource dataSource() {
        var dataSource = new DriverManagerDataSource(database.getJdbcUrl());
        dataSource.setUsername(database.getUsername());
        dataSource.setPassword(database.getPassword());
        return dataSource;
    }
}
