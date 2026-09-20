package com.sterul.opencookbookapiserver.services.suggestion;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.selection.MatchResult;

/** A recipe and how it answered the request, on its way through filtering and ranking. */
public record SuggestionCandidate(Recipe recipe, MatchResult match) {
}
