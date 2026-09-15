package com.sterul.opencookbookapiserver.unit.services.nutrition.matching;

import java.util.ArrayList;
import java.util.List;

import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.matching.ConfidenceBand;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;

/**
 * Counts weighted by occurrences.
 *
 * @param silentWrong silent links to a wrong food, or of a name no food fits
 * @param withFood    names a food fits
 */
public record MatcherEvaluation(int silentRight, int silentWrong, int uncertainRight, int uncertainWrong, int withFood,
        List<String> misses) {

    public static MatcherEvaluation of(CatalogueMatcher matcher, List<GoldNames.GoldName> names) {
        int silentRight = 0, silentWrong = 0, uncertainRight = 0, uncertainWrong = 0, withFood = 0;
        var misses = new ArrayList<String>();
        for (var name : names) {
            var linked = matcher.match(name.typed(), name.language());
            var right = linked.map(candidate -> name.expected().contains(candidate.foodKey())).orElse(false);
            if (!name.nothingFits()) {
                withFood += name.weight();
            }
            if (linked.isEmpty()) {
                if (!name.nothingFits()) {
                    misses.add("not linked: " + describe(name, matcher.rank(name.typed(), name.language(), 1)));
                }
                continue;
            }
            var silent = linked.get().band() == ConfidenceBand.SILENT;
            if (right && silent) {
                silentRight += name.weight();
            } else if (right) {
                uncertainRight += name.weight();
            } else {
                if (silent) {
                    silentWrong += name.weight();
                } else {
                    uncertainWrong += name.weight();
                }
                misses.add((silent ? "wrong silent: " : "wrong uncertain: ") + describe(name, List.of(linked.get())));
            }
        }
        return new MatcherEvaluation(silentRight, silentWrong, uncertainRight, uncertainWrong, withFood, List.copyOf(misses));
    }

    public double silentPrecision() {
        var silent = silentRight + silentWrong;
        return silent == 0 ? 1 : (double) silentRight / silent;
    }

    public double coverage() {
        return withFood == 0 ? 1 : (double) (silentRight + uncertainRight) / withFood;
    }

    @Override
    public String toString() {
        return "silent precision %.3f (%d right, %d wrong), coverage %.3f (%d of %d; %d uncertain), %d uncertain wrong"
                .formatted(silentPrecision(), silentRight, silentWrong, coverage(), silentRight + uncertainRight, withFood,
                        uncertainRight, uncertainWrong);
    }

    private static String describe(GoldNames.GoldName name, List<MatchCandidate> candidates) {
        var best = candidates.stream().findFirst()
                .map(candidate -> "%s %.2f".formatted(candidate.foodKey(), candidate.confidence()))
                .orElse("no candidate");
        return "%s (%s) expected %s, got %s".formatted(name.typed(), name.language(),
                name.nothingFits() ? "nothing" : String.join("|", name.expected()), best);
    }
}
