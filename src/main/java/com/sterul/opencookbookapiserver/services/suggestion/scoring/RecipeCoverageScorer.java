package com.sterul.opencookbookapiserver.services.suggestion.scoring;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;
import com.sterul.opencookbookapiserver.services.suggestion.CandidateScorer;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/**
 * How much of the recipe the cook already has. A dish needing four things, three of them named,
 * is closer to cookable than one needing twenty - the approximation of "nothing more to buy"
 * that is possible without knowing which staples a kitchen keeps.
 */
@Component
public class RecipeCoverageScorer implements CandidateScorer {

    public static final String TERM = "recipeCoverage";
    private static final double WEIGHT = 0.25;

    @Override
    public Optional<ScoreTerm> score(SuggestionCandidate candidate, SuggestionCriteria criteria) {
        if (!criteria.hasIngredientFilter() || candidate.match().totalLines() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ScoreTerm(TERM, WEIGHT * candidate.match().lineCoverage()));
    }
}
