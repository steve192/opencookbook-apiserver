package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.repositories.MlJobRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * The ml_job table, on a database that has only ever seen the migrations.
 *
 * The other integration tests build their schema from the entities, so they would pass against
 * a migration that named or typed a column differently - or one that was never written at all.
 * This one runs Flyway and lets Hibernate touch nothing, which is the only way the column
 * naming that production depends on is actually exercised.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class MlJobMigrationIntegrationTest {

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
    private MlJobRepository mlJobRepository;
    @Autowired
    private UserRepository userRepository;

    private static final String USER = "ml-migration-test@example.com";

    private long userId;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM ml_job");
        userId = insertUser(USER);
    }

    @Test
    void aJobSurvivesARoundTripThroughTheMigratedSchema() {
        var finishedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var saved = mlJobRepository.save(MlJob.builder()
                .id(UUID.randomUUID().toString())
                .owner(userRepository.findById(userId).orElseThrow())
                .jobType("recipe_ocr")
                .status(MlJobStatus.COMPLETED)
                .remoteJobId("remote-1")
                .result("{\"title\":{\"value\":\"Apfelkuchen\"}}")
                .errorRetryable(false)
                .finishedAt(finishedAt)
                .build());

        var readBack = mlJobRepository.findById(saved.getId()).orElseThrow();

        assertEquals("recipe_ocr", readBack.getJobType());
        assertEquals(MlJobStatus.COMPLETED, readBack.getStatus());
        assertEquals("remote-1", readBack.getRemoteJobId());
        assertEquals("{\"title\":{\"value\":\"Apfelkuchen\"}}", readBack.getResult());
        assertEquals(finishedAt, readBack.getFinishedAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(userId, readBack.getOwner().getUserId());
    }

    @Test
    void aFailedJobKeepsTheReasonItFailed() {
        var saved = mlJobRepository.save(job(MlJobStatus.FAILED, builder -> builder
                .errorCode("OCR_NO_TEXT_FOUND")
                .errorMessage("No text was found in that image")
                .errorRetryable(true)));

        var readBack = mlJobRepository.findById(saved.getId()).orElseThrow();

        assertEquals("OCR_NO_TEXT_FOUND", readBack.getErrorCode());
        assertEquals("No text was found in that image", readBack.getErrorMessage());
        assertTrue(readBack.isErrorRetryable());
    }

    @Test
    void aResultTooLargeForAnOrdinaryColumnStillFits() {
        // A multi-page scan with diagnostics is far bigger than the 255 a plain string maps to.
        var large = "x".repeat(50_000);
        var saved = mlJobRepository.save(
                job(MlJobStatus.COMPLETED, builder -> builder.result(large)));

        assertEquals(large, mlJobRepository.findById(saved.getId()).orElseThrow().getResult());
    }

    @Test
    void theAuditColumnsAreFilledInByTheMigratedSchema() {
        // createdOn is what the daily allowance is counted from, so it must actually be written.
        var saved = mlJobRepository.save(job(MlJobStatus.QUEUED, builder -> builder));

        assertTrue(mlJobRepository.countUsageSince(
                saved.getOwner(), Instant.now().minus(1, ChronoUnit.HOURS)) >= 1);
    }

    @Test
    void deletingAnAccountTakesItsJobsWithIt() {
        mlJobRepository.save(job(MlJobStatus.QUEUED, builder -> builder));

        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", userId);

        assertEquals(List.of(), mlJobRepository.findAll());
    }

    private MlJob job(MlJobStatus status, UnaryOperator<MlJob.MlJobBuilder> extra) {
        return extra.apply(MlJob.builder()
                .id(UUID.randomUUID().toString())
                .owner(userRepository.findById(userId).orElseThrow())
                .jobType("recipe_ocr")
                .status(status)).build();
    }

    private long insertUser(String emailAddress) {
        var existing = jdbcTemplate.query("SELECT user_id FROM cookpal_user WHERE email_address = ?",
                (row, index) -> row.getLong("user_id"), emailAddress);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        var id = jdbcTemplate.queryForObject("SELECT nextval('cookpal_user_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO cookpal_user (user_id, email_address, activated) VALUES (?, ?, true)",
                id, emailAddress);
        return id;
    }
}
