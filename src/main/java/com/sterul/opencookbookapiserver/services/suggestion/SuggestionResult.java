package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.List;

/**
 * @param nearMisses recipes one wanted ingredient short, kept apart from the results so
 *                   {@link MatchMode#MUST_CONTAIN} still means what it says
 */
public record SuggestionResult(long seed, List<RankedSuggestion> results, List<RankedSuggestion> nearMisses,
        PoolStats poolStats) {

    public SuggestionResult {
        results = List.copyOf(results);
        nearMisses = List.copyOf(nearMisses);
    }

    /**
     * Where the cookbook was narrowed, so an empty result can say which answer emptied it.
     *
     * @param afterFilters left by diet, time and meal
     * @param matched      of those, the ones the wanted ingredients fit
     */
    public record PoolStats(int owned, int afterFilters, int matched, int returned) {
    }
}
