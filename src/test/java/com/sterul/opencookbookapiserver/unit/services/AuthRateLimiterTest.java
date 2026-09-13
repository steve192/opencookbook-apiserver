package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.AuthRateLimiter;
import com.sterul.opencookbookapiserver.unit.MovableClock;

class AuthRateLimiterTest {

    private MovableClock clock;
    private AuthRateLimiter cut;

    @BeforeEach
    void setup() {
        clock = new MovableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var configuration = new OpencookbookConfiguration();
        configuration.getAuth().setAttemptsPerHourPerIp(3);
        configuration.getAuth().setMailsPerHourPerAddress(2);
        cut = new AuthRateLimiter(configuration, clock);
    }

    @Test
    void guessingIsAllowedUpToTheBudgetAndThenRefused() {
        for (var attempt = 0; attempt < 3; attempt++) {
            assertTrue(cut.recordAttempt("203.0.113.1").allowed());
        }

        assertFalse(cut.recordAttempt("203.0.113.1").allowed());
    }

    @Test
    void oneCallerRunningOutDoesNotShutOutTheRest() {
        for (var attempt = 0; attempt < 4; attempt++) {
            cut.recordAttempt("203.0.113.1");
        }

        assertTrue(cut.recordAttempt("203.0.113.2").allowed());
    }

    @Test
    void theBudgetComesBackWhenTheWindowHasPassed() {
        for (var attempt = 0; attempt < 4; attempt++) {
            cut.recordAttempt("203.0.113.1");
        }

        clock.advanceBy(Duration.ofHours(1).plusMinutes(1));

        assertTrue(cut.recordAttempt("203.0.113.1").allowed());
    }

    @Test
    void aRecipientCannotBeWrittenToMoreOftenThanTheirBudget() {
        assertTrue(cut.mayMail("victim@example.com"));
        assertTrue(cut.mayMail("victim@example.com"));

        assertFalse(cut.mayMail("victim@example.com"));
    }

    /** Otherwise the budget is spent once per spelling of the same inbox. */
    @Test
    void theSameInboxCountsAsOneHoweverItIsSpelled() {
        cut.mayMail("victim@example.com");
        cut.mayMail("VICTIM@Example.COM");

        assertFalse(cut.mayMail("Victim@example.com"));
    }

    @Test
    void oneRecipientRunningOutDoesNotBlockAnother() {
        cut.mayMail("victim@example.com");
        cut.mayMail("victim@example.com");
        cut.mayMail("victim@example.com");

        assertTrue(cut.mayMail("somebody-else@example.com"));
    }

    /** Guessing passwords and sending mail are separate threats and separate budgets. */
    @Test
    void spendingOneBudgetDoesNotSpendTheOther() {
        for (var attempt = 0; attempt < 4; attempt++) {
            cut.recordAttempt("203.0.113.1");
        }

        assertTrue(cut.mayMail("somebody@example.com"));
    }
}
