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

import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.services.instance.InstanceSettingsService;
import com.sterul.opencookbookapiserver.services.instance.SetupService;

/** V38 through Flyway alone, validated against the entities: the other tests insert the settings row themselves. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class InstanceSetupMigrationIntegrationTest {

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
    @Autowired
    private SetupService setupService;
    @Autowired
    private InstanceSettingsService settings;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM invitation");
        jdbcTemplate.update("DELETE FROM cookpal_user");
    }

    @Test
    void aMigratedInstanceIsOpenAndCanBeSetUp() {
        assertEquals(SignupMode.OPEN, settings.getSignupMode());

        setupService.createFirstAdministrator("migrated-admin@example.invalid", "a-password");

        assertEquals(Role.ADMIN.name(), jdbcTemplate.queryForObject(
                "SELECT roles FROM cookpal_user WHERE email_address = 'migrated-admin@example.invalid'", String.class));
    }

    @Test
    void thereIsOnlyEverOneSettingsRow() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO instance_settings (id, signup_mode) VALUES (2, 'OPEN')"));
    }

    @Test
    void anInvitationOutlivesTheAdministratorWhoCreatedIt() {
        var adminId = jdbcTemplate.queryForObject("SELECT nextval('cookpal_user_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO cookpal_user (user_id, email_address, activated) VALUES (?, ?, true)",
                adminId, "migration-invitations@example.invalid");
        jdbcTemplate.update("INSERT INTO invitation (id, created_by_user_id, expires_at) VALUES ('token', ?, now())",
                adminId);

        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", adminId);

        assertNull(jdbcTemplate.queryForObject("SELECT created_by_user_id FROM invitation WHERE id = 'token'",
                Long.class));
    }
}
