package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V40: existing accounts start their countdown at the migration, and Google-only ones count as linked. */
@Testcontainers
class AccountActivityMigrationIntegrationTest {

    // V8__.sql carries an "OWNER to cookpal", so the chain only applies as that role.
    @Container
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    @Test
    void existingAccountsCountAsActiveNowAndPasswordlessOnesAsLinkedToGoogle() {
        var dataSource = dataSource();
        migrateTo(dataSource, "39");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO cookpal_user (user_id, email_address, password_hash, activated)"
                + " VALUES (1, 'password@example.invalid', 'x', true)");
        jdbc.update("INSERT INTO cookpal_user (user_id, email_address, activated)"
                + " VALUES (2, 'google@example.invalid', true)");

        migrateTo(dataSource, "40");

        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM cookpal_user WHERE last_active_at > now() - interval '1 minute'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT last_sign_in_at FROM cookpal_user WHERE user_id = 1", Object.class));
        assertFalse(jdbc.queryForObject("SELECT google_linked FROM cookpal_user WHERE user_id = 1", Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT google_linked FROM cookpal_user WHERE user_id = 2", Boolean.class));
        assertEquals(0, jdbc.queryForObject("SELECT inactivity_notices FROM cookpal_user WHERE user_id = 2", Integer.class));
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
