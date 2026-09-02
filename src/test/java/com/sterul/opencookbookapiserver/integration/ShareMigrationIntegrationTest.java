package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.entities.sharing.ShareResourceType;
import com.sterul.opencookbookapiserver.entities.sharing.ShareVisibility;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * The migrations, on a database that has only ever seen the migrations.
 *
 * The rest of the integration tests build their schema from the entities, which means they would
 * pass just as happily against a migration that was never written. This one runs Flyway on a
 * container of its own and lets Hibernate touch nothing, so what is exercised is the schema that
 * production will actually have.
 *
 * Two things are checked. That {@link Share} maps onto the migrated table - written and read back
 * through the repository, which is what would fail if a column were named or typed differently in
 * the migration than in the entity. And the rules that exist only in SQL: a partial unique index,
 * two check constraints and a cascading foreign key, none of which can be expressed on the entity.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class ShareMigrationIntegrationTest {

    // The username matters: V8__.sql carries an "OWNER to cookpal" left over from pgAdmin, so the
    // chain only applies on a database whose role is the one production uses.
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
    private ShareRepository shareRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;

    private static final String USER = "migration-test@example.com";

    private long userId;
    private long recipeId;
    private long otherRecipeId;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM share");
        userId = insertUser(USER);
        recipeId = insertRecipe("Migrated recipe", userId);
        otherRecipeId = insertRecipe("Another migrated recipe", userId);
    }

    @Test
    void aShareSurvivesARoundTripThroughTheMigratedSchema() {
        var expiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        var saved = shareRepository.save(Share.builder()
                .owner(userRepository.findById(userId).orElseThrow())
                .resourceType(ShareResourceType.RECIPE)
                .visibility(ShareVisibility.PUBLIC_LINK)
                .recipe(recipeRepository.findById(recipeId).orElseThrow())
                .expiresAt(expiry)
                .accessCount(7)
                .build());

        var readBack = shareRepository.findById(saved.getId()).orElseThrow();

        assertEquals(ShareResourceType.RECIPE, readBack.getResourceType());
        assertEquals(ShareVisibility.PUBLIC_LINK, readBack.getVisibility());
        assertEquals(recipeId, readBack.getRecipe().getId());
        assertEquals(expiry, readBack.getExpiresAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(7, readBack.getAccessCount());
    }

    @Test
    void aRecipeCannotHaveTwoPublicLinks() {
        insertShare("first", recipeId);

        // The service decides by reading and then writing; two requests arriving together would
        // both find no share. Only the database can make the second one lose.
        assertThrows(DataIntegrityViolationException.class, () -> insertShare("second", recipeId));
    }

    @Test
    void differentRecipesEachHaveTheirOwnLink() {
        insertShare("first", recipeId);
        insertShare("second", otherRecipeId);

        assertEquals(2, jdbcTemplate.queryForObject("SELECT count(*) FROM share", Long.class));
    }

    @Test
    void aRecipeShareWithoutARecipeIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> insertShare("orphan", null));
    }

    @Test
    void anUnknownVisibilityIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO share (id, resource_type, visibility, expires_at, access_count, owner_user_id, recipe_id)"
                        + " VALUES (?, 'RECIPE', 'EVERYBODY_FOREVER', ?, 0, ?, ?)",
                "invalid", expiryTimestamp(), userId, recipeId));
    }

    @Test
    void deletingTheRecipeCascadesToItsShares() {
        insertShare("cascading", recipeId);

        jdbcTemplate.update("DELETE FROM recipe WHERE id = ?", recipeId);

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM share", Long.class));
    }

    private void insertShare(String id, Long recipe) {
        jdbcTemplate.update(
                "INSERT INTO share (id, resource_type, visibility, expires_at, access_count, owner_user_id, recipe_id)"
                        + " VALUES (?, 'RECIPE', 'PUBLIC_LINK', ?, 0, ?, ?)",
                id, expiryTimestamp(), userId, recipe);
    }

    private Timestamp expiryTimestamp() {
        return Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS));
    }

    private long insertUser(String emailAddress) {
        // Every test method runs setup again against the same container, so this has to be the
        // same user each time rather than another one with the same address.
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

    private long insertRecipe(String title, long ownerId) {
        var id = jdbcTemplate.queryForObject("SELECT nextval('recipe_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO recipe (id, title, servings, owner_user_id) VALUES (?, ?, 1, ?)",
                id, title, ownerId);
        return id;
    }
}
