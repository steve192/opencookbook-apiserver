package com.sterul.opencookbookapiserver.services.suggestion.scoring;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.services.selection.NutritionFit;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;
import com.sterul.opencookbookapiserver.services.suggestion.NutritionAwareScorer;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCandidate;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

/** How a recipe's energy is split between the macronutrients, against the style the cook wants. */
@Component
public class MacroFitScorer implements NutritionAwareScorer {

    public static final String TERM = "macroFit";
    private static final double WEIGHT = 0.3;

    @Override
    public Optional<ScoreTerm> score(SuggestionCandidate candidate, RecipeNutritionSummary nutrition,
            SuggestionCriteria criteria) {
        return NutritionFit.macros(nutrition, criteria.macroStyle()).map(fit -> new ScoreTerm(TERM, WEIGHT * fit));
    }
}
