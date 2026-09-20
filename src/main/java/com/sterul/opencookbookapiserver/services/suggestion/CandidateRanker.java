package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutritionSummaries;

/**
 * Ranks in two passes: cheap terms order everything, nutrition terms reorder only the head. A missing
 * summary is computed on read, so the head bounds that cost however large the cookbook is.
 */
@Component
public class CandidateRanker {

    /** Below this rank a nutrition term cannot lift a candidate into a result page anyway. */
    private static final int NUTRITION_BUDGET = 50;

    private final List<CandidateScorer> scorers;
    private final List<NutritionAwareScorer> nutritionScorers;
    private final Optional<RecipeNutritionSummaries> summaries;

    public CandidateRanker(List<CandidateScorer> scorers, List<NutritionAwareScorer> nutritionScorers,
            Optional<RecipeNutritionSummaries> summaries) {
        this.scorers = scorers;
        this.nutritionScorers = nutritionScorers;
        this.summaries = summaries;
    }

    public List<RankedSuggestion> rank(List<SuggestionCandidate> candidates, SuggestionCriteria criteria) {
        var ranked = sorted(candidates.stream().map(candidate -> score(candidate, criteria)));
        if (!usesNutrition(criteria)) {
            return ranked;
        }
        var head = ranked.subList(0, Math.min(NUTRITION_BUDGET, ranked.size()));
        return sorted(head.stream().map(suggestion -> rescore(suggestion, criteria)));
    }

    private RankedSuggestion score(SuggestionCandidate candidate, SuggestionCriteria criteria) {
        return RankedSuggestion.of(candidate, scorers.stream()
                .map(scorer -> scorer.score(candidate, criteria))
                .flatMap(Optional::stream)
                .toList());
    }

    private RankedSuggestion rescore(RankedSuggestion suggestion, SuggestionCriteria criteria) {
        var nutrition = summaries.orElseThrow().of(suggestion.recipe());
        return suggestion.plus(nutritionScorers.stream()
                .map(scorer -> scorer.score(suggestion.candidate(), nutrition, criteria))
                .flatMap(Optional::stream)
                .toList());
    }

    private boolean usesNutrition(SuggestionCriteria criteria) {
        return criteria.wantsNutrition() && summaries.isPresent() && !nutritionScorers.isEmpty();
    }

    /** Recipe id breaks ties, so an unchanged cookbook and seed give an unchanged order. */
    private static List<RankedSuggestion> sorted(Stream<RankedSuggestion> ranked) {
        return ranked.sorted(Comparator.comparingDouble(RankedSuggestion::score).reversed()
                .thenComparing(suggestion -> suggestion.recipe().getId()))
                .toList();
    }
}
