package com.sterul.opencookbookapiserver.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Migrations tested on a database at the version before them, filled by the test. */
@Testcontainers
abstract class MigrationFromVersionTestBase {

    // V8__.sql carries an "OWNER to cookpal", so the chain only applies as that role.
    @Container
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cookpal")
            .withUsername("cookpal")
            .withPassword("password")
            .waitingFor(Wait.forListeningPort());

    protected JdbcTemplate jdbc;

    private final MigrationVersion versionBefore;

    protected MigrationFromVersionTestBase(String versionBefore) {
        this.versionBefore = MigrationVersion.fromVersion(versionBefore);
    }

    @BeforeEach
    void migrateToTheVersionBefore() {
        flyway(versionBefore).clean();
        flyway(versionBefore).migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    }

    protected void migrate() {
        flyway(MigrationVersion.LATEST).migrate();
    }

    protected Flyway flyway(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .cleanDisabled(false)
                .load();
    }

    protected long nextval(String sequence) {
        return jdbc.queryForObject("SELECT nextval('" + sequence + "')", Long.class);
    }
}
