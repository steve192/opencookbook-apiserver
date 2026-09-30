package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** V35 through Flyway alone, validated against the entities. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class ApiKeyMigrationIntegrationTest {

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

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM api_key");
        jdbcTemplate.update("DELETE FROM cookpal_user");
        annaId = jdbcTemplate.queryForObject("SELECT nextval('cookpal_user_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO cookpal_user (user_id, email_address, activated) VALUES (?, ?, true)",
                annaId, "migration-keys@example.invalid");
    }

    @Test
    void aSecretHashIsUnique() {
        insertKey("same-hash");

        assertThrows(DataIntegrityViolationException.class, () -> insertKey("same-hash"));
    }

    @Test
    void aDeletedAccountTakesItsKeysAndTheirScopes() {
        insertKey("hash");

        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", annaId);

        assertEquals(0, count("api_key"));
        assertEquals(0, count("api_key_scope"));
    }

    private void insertKey(String hash) {
        var id = jdbcTemplate.queryForObject("SELECT nextval('api_key_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO api_key (id, owner_user_id, name, secret_hash, display_prefix) "
                + "VALUES (?, ?, 'Home Assistant', ?, 'cpk_abcdef')", id, annaId, hash);
        jdbcTemplate.update("INSERT INTO api_key_scope (api_key_id, scope) VALUES (?, 'SHOPPING_READ')", id);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
