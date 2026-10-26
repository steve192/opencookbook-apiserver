package com.sterul.opencookbookapiserver.entities.shopping;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How often a list buys a name, learned from its purchases. The rhythm is the median gap between shopping trips,
 * so one holiday does not move it while a lasting change does after a few purchases.
 *
 * @param every      the usual gap between two purchases
 * @param lastBought the latest purchase
 */
public record Rhythm(Duration every, Instant lastBought) {

    /** Purchases closer together than this were one trip. */
    static final Duration TRIP = Duration.ofDays(1);
    static final int MIN_TRIPS = 3;
    static final int RECENT_GAPS = 5;
    static final Duration SHORTEST = Duration.ofDays(2);
    static final Duration LONGEST = Duration.ofDays(90);
    /** The gaps' median absolute deviation, as a share of their median, beyond which they do not agree. */
    static final double MAX_SPREAD = 0.5;
    static final Duration MAX_LEAD = Duration.ofDays(1);
    static final Duration SHORTEST_SHOWN = Duration.ofDays(2);
    static final Duration LONGEST_SHOWN = Duration.ofDays(7);
    static final Duration SHORTEST_SNOOZE = Duration.ofDays(1);
    static final int DORMANT_AFTER_RHYTHMS = 3;

    /** Empty when the purchases are too few, too irregular, or too far apart or close together to be a habit. */
    public static Optional<Rhythm> of(List<Instant> purchases) {
        var trips = trips(purchases);
        if (trips.size() < MIN_TRIPS) {
            return Optional.empty();
        }
        var gaps = new ArrayList<Long>();
        for (var index = Math.max(1, trips.size() - RECENT_GAPS); index < trips.size(); index++) {
            gaps.add(Duration.between(trips.get(index - 1), trips.get(index)).toSeconds());
        }
        var median = median(gaps);
        var spread = median(gaps.stream().map(gap -> Math.abs(gap - median)).toList());
        var every = Duration.ofSeconds(median);
        if (spread > median * MAX_SPREAD || every.compareTo(SHORTEST) < 0 || every.compareTo(LONGEST) > 0) {
            return Optional.empty();
        }
        return Optional.of(new Rhythm(every, purchases.stream().max(Comparator.naturalOrder()).orElseThrow()));
    }

    /** A day ahead of being due, or a quarter of a short rhythm, so it is on the list when the trip is planned. */
    public Instant suggestFrom() {
        return lastBought.plus(every).minus(min(every.dividedBy(4), MAX_LEAD));
    }

    /** How long a suggestion nobody answers is shown. */
    public Duration shownFor() {
        return min(max(every.dividedBy(2), SHORTEST_SHOWN), LONGEST_SHOWN);
    }

    /** How long "not now" holds. */
    public Duration snooze() {
        return max(every.dividedBy(2), SHORTEST_SNOOZE);
    }

    /** A stretch of time the suggestion is shown in. */
    public record Window(Instant from, Instant until) {
    }

    /**
     * When to suggest the name from {@code first} on: shown for a while, back one rhythm later if nobody answered,
     * until several rhythms without a purchase say the list has stopped buying it.
     */
    public List<Window> windowsFrom(Instant first) {
        var dormant = lastBought.plus(every.multipliedBy(DORMANT_AFTER_RHYTHMS));
        var windows = new ArrayList<Window>();
        for (var from = first; from.isBefore(dormant); from = from.plus(every)) {
            var until = from.plus(shownFor());
            windows.add(new Window(from, until.isBefore(dormant) ? until : dormant));
        }
        return windows;
    }

    public int everyDays() {
        return (int) Math.round(every.toSeconds() / (double) Duration.ofDays(1).toSeconds());
    }

    private static List<Instant> trips(List<Instant> purchases) {
        var trips = new ArrayList<Instant>();
        purchases.stream().sorted().forEach(purchase -> {
            if (trips.isEmpty() || !purchase.isBefore(trips.getLast().plus(TRIP))) {
                trips.add(purchase);
            }
        });
        return trips;
    }

    private static Duration min(Duration one, Duration other) {
        return one.compareTo(other) <= 0 ? one : other;
    }

    private static Duration max(Duration one, Duration other) {
        return one.compareTo(other) >= 0 ? one : other;
    }

    private static long median(List<Long> values) {
        var sorted = values.stream().sorted().toList();
        var middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
