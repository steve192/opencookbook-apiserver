package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.List;
import java.util.stream.Stream;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** A candidate with the terms that placed it where it is. */
public record RankedSuggestion(SuggestionCandidate candidate, double score, List<ScoreTerm> terms) {

    public RankedSuggestion {
        terms = List.copyOf(terms);
    }

    public static RankedSuggestion of(SuggestionCandidate candidate, List<ScoreTerm> terms) {
        return new RankedSuggestion(candidate, ScoreTerm.total(terms), terms);
    }

    public Recipe recipe() {
        return candidate.recipe();
    }

    public RankedSuggestion plus(List<ScoreTerm> extra) {
        return of(candidate, Stream.concat(terms.stream(), extra.stream()).toList());
    }
}
