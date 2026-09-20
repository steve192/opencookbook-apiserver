package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.Optional;

import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** A ranking term needing the recipe's nutrition, which is read only for the candidates that already rank near the top. */
public interface NutritionAwareScorer {

    Optional<ScoreTerm> score(SuggestionCandidate candidate, RecipeNutritionSummary nutrition, SuggestionCriteria criteria);
}
