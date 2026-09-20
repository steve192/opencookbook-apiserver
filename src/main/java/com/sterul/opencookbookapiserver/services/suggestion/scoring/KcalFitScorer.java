package com.sterul.opencookbookapiserver.services.suggestion.scoring;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.services.selection.NutritionFit;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;
import com.sterul.opencookbookapiserver.services.suggestion.NutritionAwareScorer;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/** How near a recipe lands to the energy per serving the cook asked for. */
@Component
public class KcalFitScorer implements NutritionAwareScorer {

    public static final String TERM = "kcalFit";
    private static final double WEIGHT = 0.4;

    @Override
    public Optional<ScoreTerm> score(SuggestionCandidate candidate, RecipeNutritionSummary nutrition,
            SuggestionCriteria criteria) {
        if (criteria.targetKcalPerServing() == null) {
            return Optional.empty();
        }
        return NutritionFit.kcal(nutrition, criteria.targetKcalPerServing()).map(fit -> new ScoreTerm(TERM, WEIGHT * fit));
    }
}
