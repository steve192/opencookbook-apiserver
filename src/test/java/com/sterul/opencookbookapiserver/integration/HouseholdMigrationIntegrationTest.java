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

import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * V27 through Flyway alone: the other household tests build their schema from the entities and
 * would pass without the migration. Pins the constraints and cascades only SQL can express.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("integration-test")
@DirtiesContext
@Testcontainers
class HouseholdMigrationIntegrationTest {

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
    private UserRepository userRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;

    private static final String USER = "household-migration@example.com";

    private long userId;
    private long recipeId;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM weekplan_day");
        jdbcTemplate.update("DELETE FROM household_invite");
        jdbcTemplate.update("DELETE FROM household_membership");
        jdbcTemplate.update("DELETE FROM household");
        userId = insertUser(USER);
        recipeId = insertRecipe("Migrated recipe", userId);
    }

    @Test
    void aHouseholdAndItsMembershipSurviveARoundTrip() {
        var household = householdRepository.save(Household.builder().name("Familie Test").build());
        var saved = membershipRepository.save(HouseholdMembership.builder()
                .household(household)
                .member(userRepository.findById(userId).orElseThrow())
                .shareRecipes(true)
                .build());

        // Read through the query the access rule uses, which fetches the member with it; the
        // associations are lazy, and a detached proxy would say nothing about the columns.
        var readBack = membershipRepository.findAllInHousehold(household.getId()).stream()
                .filter(membership -> membership.getId().equals(saved.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals("Familie Test", householdRepository.findById(household.getId())
                .orElseThrow().getName());
        assertEquals(userId, readBack.getMember().getUserId());
        assertEquals(true, readBack.isShareRecipes());
    }

    @Test
    void nobodyIsInTheSameHouseholdTwice() {
        var householdId = insertHousehold("Familie Test");
        insertMembership(householdId, userId);

        // The service checks by reading and then writing; two requests arriving together would
        // both find no membership. Only the database can make the second one lose.
        assertThrows(DataIntegrityViolationException.class, () -> insertMembership(householdId, userId));
    }

    @Test
    void aHouseholdHasOneWeekplanDayPerDate() {
        var householdId = insertHousehold("Familie Test");
        insertWeekplanDay(null, householdId);

        // Two members adding the first meal of a day at once would both find no day to update.
        assertThrows(DataIntegrityViolationException.class, () -> insertWeekplanDay(null, householdId));
    }

    @Test
    void aPlanBelongsToExactlyOneOfAPersonAndAHousehold() {
        var householdId = insertHousehold("Familie Test");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertWeekplanDay(userId, householdId), "Both is not a scope");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertWeekplanDay(null, null), "Neither is not a scope either");
    }

    @Test
    void aHouseholdEndingTakesItsMembershipsInvitesAndWeekWithIt() {
        var householdId = insertHousehold("Familie Test");
        insertMembership(householdId, userId);
        insertInvite(householdId);
        insertWeekplanDay(null, householdId);

        jdbcTemplate.update("DELETE FROM household WHERE id = ?", householdId);

        assertEquals(0, count("household_membership"));
        assertEquals(0, count("household_invite"));
        assertEquals(0, count("weekplan_day"));
    }

    @Test
    void aHouseholdEndingTakesNoRecipeWithIt() {
        var householdId = insertHousehold("Familie Test");
        insertMembership(householdId, userId);

        jdbcTemplate.update("DELETE FROM household WHERE id = ?", householdId);

        // The whole reason leaving is safe: a household owns nothing, so every member keeps the
        // cookbook they came with.
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recipe WHERE id = ?", Long.class, recipeId));
    }

    @Test
    void anAccountGoingTakesItsMembershipsButLeavesTheHousehold() {
        var householdId = insertHousehold("Familie Test");
        var otherUserId = insertUser("household-migration-other@example.com");
        insertMembership(householdId, userId);
        insertMembership(householdId, otherUserId);

        jdbcTemplate.update("DELETE FROM recipe WHERE owner_user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", userId);

        assertEquals(1, count("household_membership"));
        assertEquals(1, count("household"));
    }

    @Test
    void anInviteOutlivesTheMemberWhoMadeIt() {
        var householdId = insertHousehold("Familie Test");
        insertInvite(householdId);

        jdbcTemplate.update("DELETE FROM recipe WHERE owner_user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM cookpal_user WHERE user_id = ?", userId);

        assertEquals(1, count("household_invite"));
        assertEquals(null, jdbcTemplate.queryForObject(
                "SELECT created_by_user_id FROM household_invite", Long.class));
    }

    // ------------------------------------------------------------------------------- helpers

    private Long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String insertHousehold(String name) {
        var id = "household-" + name.hashCode();
        jdbcTemplate.update("INSERT INTO household (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private void insertMembership(String householdId, long memberId) {
        var id = jdbcTemplate.queryForObject("SELECT nextval('household_membership_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO household_membership (id, household_id, member_user_id, share_recipes)"
                + " VALUES (?, ?, ?, true)", id, householdId, memberId);
    }

    private void insertInvite(String householdId) {
        jdbcTemplate.update("INSERT INTO household_invite (id, household_id, created_by_user_id, expires_at)"
                + " VALUES (?, ?, ?, ?)", "invite-" + householdId, householdId, userId,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
    }

    private void insertWeekplanDay(Long ownerId, String householdId) {
        var id = jdbcTemplate.queryForObject("SELECT nextval('weekplan_day_seq')", Long.class);
        jdbcTemplate.update("INSERT INTO weekplan_day (id, plan_date, owner_user_id, household_id)"
                + " VALUES (?, DATE '2026-10-05', ?, ?)", id, ownerId, householdId);
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
