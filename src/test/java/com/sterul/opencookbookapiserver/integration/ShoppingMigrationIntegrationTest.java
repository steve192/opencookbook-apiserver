package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V31 and V32 through Flyway alone, validated against the entities: the other shopping tests build
 * their schema from the entities and would pass without the migrations.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class ShoppingMigrationIntegrationTest {

    // V8__.sql carries an "OWNER to cookpal", so the chain only applies as that role.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> migratedDatabase = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long annaId;
    private long bertId;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM shopping_list");
        jdbcTemplate.update("DELETE FROM household");
        jdbcTemplate.update("DELETE FROM cookpal_user");
        annaId = insertUser("migration-anna@example.invalid");
        bertId = insertUser("migration-bert@example.invalid");
    }

    @Test
    void aScopeHasOneDefaultList() {
        insertList(annaId, null, true);

        assertThrows(DataIntegrityViolationException.class, () -> insertList(annaId, null, true));
    }

    @Test
    void aListBelongsToExactlyOneScope() {
        assertThrows(DataIntegrityViolationException.class, () -> insertList(null, null, false));
    }

    @Test
    void aNameIsOnAListOnceButMayReturnAfterADelete() {
        var list = insertList(annaId, null, true);
        insertItem("a", list, "milch", true, annaId);
        insertItem("b", list, "milch", false, annaId);

        assertThrows(DataIntegrityViolationException.class, () -> insertItem("c", list, "milch", false, annaId));
    }

    @Test
    void anEndingHouseholdTakesItsListsAndAGoneMemberLeavesTheirItems() {
        jdbcTemplate.update("INSERT INTO household (id, name) VALUES ('h1', 'Familie Test')");
        var shared = insertList(null, "h1", true);
        insertItem("from-bert", shared, "brot", false, bertId);
        jdbcTemplate.update("INSERT INTO shopping_item_source (shopping_item_id, title) VALUES ('from-bert', 'Suppe')");

        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", bertId);
        assertNull(jdbcTemplate.queryForObject("SELECT added_by_user_id FROM shopping_item WHERE id = 'from-bert'",
                Long.class));

        jdbcTemplate.update("DELETE FROM household WHERE id = 'h1'");
        assertEquals(0, count("shopping_list"));
        assertEquals(0, count("shopping_item_source"));
    }

    @Test
    void aDeletedAccountTakesItsListsAndStaples() {
        insertList(annaId, null, true);
        jdbcTemplate.update("INSERT INTO shopping_staple (id, user_id, name_key, name) "
                + "VALUES (nextval('shopping_staple_seq'), ?, 'salz', 'Salz')", annaId);

        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", annaId);

        assertEquals(0, count("shopping_list"));
        assertEquals(0, count("shopping_staple"));
    }

    private long insertUser(String emailAddress) {
        var id = jdbcTemplate.queryForObject("SELECT nextval('cookpal_user_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO cookpal_user (user_id, email_address, activated) VALUES (?, ?, true)",
                id, emailAddress);
        return id;
    }

    private long insertList(Long ownerId, String householdId, boolean defaultList) {
        var id = jdbcTemplate.queryForObject("SELECT nextval('shopping_list_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO shopping_list (id, owner_user_id, household_id, default_list) VALUES (?, ?, ?, ?)",
                id, ownerId, householdId, defaultList);
        return id;
    }

    private void insertItem(String id, long listId, String nameKey, boolean deleted, long addedBy) {
        jdbcTemplate.update("INSERT INTO shopping_item (id, list_id, name, name_key, aisle, status, version, deleted, "
                + "added_by_user_id) VALUES (?, ?, ?, ?, 'OTHER', 'ACTIVE', 1, ?, ?)",
                id, listId, nameKey, nameKey, deleted, addedBy);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
