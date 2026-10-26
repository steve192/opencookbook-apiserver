package com.sterul.opencookbookapiserver.unit.entities.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.shopping.Rhythm;

class RhythmTest {

    private static final Instant START = Instant.parse("2026-09-05T10:00:00Z");

    @Test
    void threePurchasesAtAgreeingGapsMakeARhythm() {
        var rhythm = Rhythm.of(daysAfterStart(0, 7, 14)).orElseThrow();

        assertEquals(Duration.ofDays(7), rhythm.every());
        assertEquals(START.plus(Duration.ofDays(14)), rhythm.lastBought());
        assertEquals(7, rhythm.everyDays());
    }

    @Test
    void twoPurchasesAreNotYetAHabit() {
        assertTrue(Rhythm.of(daysAfterStart(0, 7)).isEmpty());
    }

    @Test
    void purchasesOnOneDayAreOneTrip() {
        var purchases = List.of(START, START.plus(Duration.ofHours(5)), START.plus(Duration.ofDays(7)));

        assertTrue(Rhythm.of(purchases).isEmpty());
    }

    @Test
    void aSkippedWeekDoesNotMoveTheRhythm() {
        assertEquals(Duration.ofDays(7), Rhythm.of(daysAfterStart(0, 7, 14, 28)).orElseThrow().every());
    }

    @Test
    void gapsThatDisagreeMakeNoRhythm() {
        assertTrue(Rhythm.of(daysAfterStart(0, 3, 23)).isEmpty());
        assertTrue(Rhythm.of(daysAfterStart(0, 5, 14)).isPresent());
    }

    @Test
    void onlyTheRecentGapsCountSoTheRhythmFollowsAChange() {
        var weeklyThenEveryFourDays = daysAfterStart(0, 7, 14, 21, 25, 29, 33, 37);

        assertEquals(Duration.ofDays(4), Rhythm.of(weeklyThenEveryFourDays).orElseThrow().every());
    }

    @Test
    void daysApartOrMonthsApartIsNoHabitToRemindOf() {
        assertTrue(Rhythm.of(daysAfterStart(0, 1, 2, 3)).isEmpty());
        assertTrue(Rhythm.of(daysAfterStart(0, 100, 200)).isEmpty());
    }

    @Test
    void aWeeklyItemIsSuggestedADayAheadAndAShortRhythmAQuarterAhead() {
        var weekly = new Rhythm(Duration.ofDays(7), START);
        var everyFewDays = new Rhythm(Duration.ofDays(4), START);

        assertEquals(START.plus(Duration.ofDays(6)), weekly.suggestFrom());
        assertEquals(START.plus(Duration.ofDays(3)), everyFewDays.suggestFrom());
    }

    @Test
    void aSuggestionStaysForHalfItsRhythmWithinBounds() {
        assertEquals(Duration.ofHours(84), new Rhythm(Duration.ofDays(7), START).shownFor());
        assertEquals(Duration.ofDays(2), new Rhythm(Duration.ofDays(2), START).shownFor());
        assertEquals(Duration.ofDays(7), new Rhythm(Duration.ofDays(30), START).shownFor());
        assertEquals(Duration.ofDays(1), new Rhythm(Duration.ofDays(2), START).snooze());
        assertEquals(Duration.ofDays(15), new Rhythm(Duration.ofDays(30), START).snooze());
    }

    @Test
    void anUnansweredSuggestionComesBackEachRhythmUntilThreeRhythmsWithoutAPurchase() {
        var weekly = new Rhythm(Duration.ofDays(7), START);

        assertEquals(List.of(
                new Rhythm.Window(START.plus(Duration.ofDays(6)), START.plus(Duration.ofDays(6).plusHours(84))),
                new Rhythm.Window(START.plus(Duration.ofDays(13)), START.plus(Duration.ofDays(13).plusHours(84))),
                new Rhythm.Window(START.plus(Duration.ofDays(20)), START.plus(Duration.ofDays(21)))),
                weekly.windowsFrom(weekly.suggestFrom()));
    }

    @Test
    void noWindowOpensOnceTheListSeemsToHaveStoppedBuying() {
        var weekly = new Rhythm(Duration.ofDays(7), START);

        assertTrue(weekly.windowsFrom(START.plus(Duration.ofDays(21))).isEmpty());
    }

    private static List<Instant> daysAfterStart(long... days) {
        return LongStream.of(days).mapToObj(day -> START.plus(Duration.ofDays(day))).toList();
    }
}
