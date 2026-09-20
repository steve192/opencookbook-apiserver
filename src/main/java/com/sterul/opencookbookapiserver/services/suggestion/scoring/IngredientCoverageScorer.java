package com.sterul.opencookbookapiserver.services.suggestion.scoring;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;
import com.sterul.opencookbookapiserver.services.suggestion.CandidateScorer;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/** How many of the wanted ingredients a recipe uses. The dominant term whenever any were named. */
@Component
public class IngredientCoverageScorer implements CandidateScorer {

    public static final String TERM = "ingredientCoverage";
    private static final double WEIGHT = 1.0;

    @Override
    public Optional<ScoreTerm> score(SuggestionCandidate candidate, SuggestionCriteria criteria) {
        if (!criteria.hasIngredientFilter()) {
            return Optional.empty();
        }
        return Optional.of(new ScoreTerm(TERM, WEIGHT * candidate.match().targetCoverage()));
    }
}
