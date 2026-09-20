package com.sterul.opencookbookapiserver.services.suggestion.scoring;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.selection.RecipeSuitability;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;
import com.sterul.opencookbookapiserver.services.suggestion.CandidateScorer;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/**
 * Rewards a recipe somebody actually marked as this meal. Recipes with no meal type are already
 * eligible, so scoring them zero ranks them below a stated match without hiding them.
 */
@Component
public class MealTypeFitScorer implements CandidateScorer {

    public static final String TERM = "mealTypeFit";
    private static final double WEIGHT = 0.3;

    @Override
    public Optional<ScoreTerm> score(SuggestionCandidate candidate, SuggestionCriteria criteria) {
        if (criteria.mealTypes().isEmpty()) {
            return Optional.empty();
        }
        var stated = RecipeSuitability.isMarkedFor(candidate.recipe(), criteria.mealTypes());
        return Optional.of(new ScoreTerm(TERM, stated ? WEIGHT : 0));
    }
}
