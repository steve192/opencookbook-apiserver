package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.mail.MailAllowance;
import com.sterul.opencookbookapiserver.unit.MovableClock;

class MailAllowanceTest {

    private MailAllowance cut;

    @BeforeEach
    void setup() {
        var configuration = new OpencookbookConfiguration();
        configuration.getAuth().setMailsPerHourPerAddress(2);
        cut = new MailAllowance(configuration, new MovableClock(Instant.parse("2026-01-01T00:00:00Z")));
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
}
