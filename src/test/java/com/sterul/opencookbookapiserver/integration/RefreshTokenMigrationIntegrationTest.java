package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.services.SecretTokens;

/**
 * V36 keeps refresh tokens as hashes. Apps signed in before it must stay signed in, so the tokens
 * they hold have to arrive as the hash they will be looked up by; expired ones may go.
 */
@Testcontainers
class RefreshTokenMigrationIntegrationTest {

    // V8__.sql carries an "OWNER to cookpal", so the chain only applies as that role.
    @Container
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    @Test
    void aTokenHandedOutBeforeIsFoundByItsHashAfterwards() {
        var dataSource = dataSource();
        migrateTo(dataSource, "35");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO cookpal_user (user_id, email_address, password_hash, activated)"
                + " VALUES (1, 'migration@example.invalid', 'x', true)");
        jdbc.update("INSERT INTO refresh_token (token, owner_user_id, valid_until)"
                + " VALUES ('a-live-token', 1, now() + interval '30 days')");
        jdbc.update("INSERT INTO refresh_token (token, owner_user_id, valid_until)"
                + " VALUES ('an-expired-token', 1, now() - interval '1 day')");

        migrateTo(dataSource, "36");

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class));
        assertEquals(SecretTokens.hash("a-live-token"),
                jdbc.queryForObject("SELECT token_hash FROM refresh_token", String.class));
        assertNotNull(jdbc.queryForObject("SELECT session_id FROM refresh_token", String.class));

        jdbc.update("DELETE FROM cookpal_user WHERE user_id = 1");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class),
                "an account takes its sign ins with it");
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
