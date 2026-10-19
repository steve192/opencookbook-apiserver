package com.sterul.opencookbookapiserver.unit.ratelimiting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.ratelimiting.RequestBudget;
import com.sterul.opencookbookapiserver.unit.MovableClock;

class RequestBudgetTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-09-01T10:00:00Z"));

    /** Four an hour refill one every quarter of an hour. */
    private final RequestBudget cut = new RequestBudget(4, Duration.ofHours(1), clock);

    @Test
    void aKeyMayGoUpToThePermitsAndIsThenToldWhenTheNextOneIsDue() {
        for (var request = 0; request < 4; request++) {
            assertTrue(cut.spend("203.0.113.1").allowed());
        }

        var refused = cut.spend("203.0.113.1");

        assertFalse(refused.allowed());
        assertEquals(Duration.ofMinutes(15), refused.retryAfter());
    }

    @Test
    void permitsComeBackEvenlyOverThePeriod() {
        for (var request = 0; request < 4; request++) {
            cut.spend("203.0.113.1");
        }

        clock.advanceBy(Duration.ofMinutes(30));

        assertTrue(cut.spend("203.0.113.1").allowed());
        assertTrue(cut.spend("203.0.113.1").allowed());
        assertFalse(cut.spend("203.0.113.1").allowed());
    }

    @Test
    void aPeriodLaterTheWholeBudgetIsBack() {
        for (var request = 0; request < 5; request++) {
            cut.spend("203.0.113.1");
        }

        clock.advanceBy(Duration.ofHours(1));

        for (var request = 0; request < 4; request++) {
            assertTrue(cut.spend("203.0.113.1").allowed());
        }
    }

    @Test
    void oneKeyRunningOutLeavesTheOthersAlone() {
        for (var request = 0; request < 5; request++) {
            cut.spend("203.0.113.1");
        }

        assertTrue(cut.spend("203.0.113.2").allowed());
    }

    @Test
    void aDailyBudgetRefillsOverTheDay() {
        var daily = new RequestBudget(2, Duration.ofDays(1), clock);
        daily.spend("share");
        daily.spend("share");

        var refused = daily.spend("share");

        assertFalse(refused.allowed());
        assertEquals(Duration.ofHours(12), refused.retryAfter());
        clock.advanceBy(Duration.ofHours(12));
        assertTrue(daily.spend("share").allowed());
    }
}
