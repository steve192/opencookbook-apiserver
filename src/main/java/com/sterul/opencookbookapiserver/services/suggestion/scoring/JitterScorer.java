package com.sterul.opencookbookapiserver.services.suggestion.scoring;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.selection.Jitter;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;
import com.sterul.opencookbookapiserver.services.suggestion.CandidateScorer;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/** Keeps equally good recipes from always appearing in the same order; see {@link Jitter}. */
@Component
public class JitterScorer implements CandidateScorer {

    public static final String TERM = "jitter";
    private static final double WEIGHT = 0.05;

    @Override
    public Optional<ScoreTerm> score(SuggestionCandidate candidate, SuggestionCriteria criteria) {
        var draw = Jitter.draw(criteria.seed(), candidate.recipe().getId());
        return Optional.of(new ScoreTerm(TERM, WEIGHT * draw));
    }
}
