package com.sterul.opencookbookapiserver.services.selection;

import java.util.List;

/**
 * How one recipe answers what a cook asked for.
 *
 * @param matchedLines the recipe's own lines a wanted ingredient covers
 * @param totalLines   its lines that name an ingredient at all
 */
public record MatchResult(List<MatchTarget> matched, List<MatchTarget> missing, int matchedLines, int totalLines) {

    public MatchResult {
        matched = List.copyOf(matched);
        missing = List.copyOf(missing);
    }

    public int wanted() {
        return matched.size() + missing.size();
    }

    /** Share of the wanted ingredients this recipe has. */
    public double targetCoverage() {
        return wanted() == 0 ? 0 : (double) matched.size() / wanted();
    }

    /** Share of the recipe's own ingredients the cook already named. */
    public double lineCoverage() {
        return totalLines == 0 ? 0 : (double) matchedLines / totalLines;
    }

    public boolean hasAny() {
        return !matched.isEmpty();
    }

    public boolean hasAll() {
        return missing.isEmpty();
    }
}
