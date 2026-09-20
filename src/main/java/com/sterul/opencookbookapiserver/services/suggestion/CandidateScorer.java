package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.Optional;

import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** One ranking term. Adding a term is adding a bean; nothing else changes. */
public interface CandidateScorer {

    /** Empty when the request says nothing this term could judge. */
    Optional<ScoreTerm> score(SuggestionCandidate candidate, SuggestionCriteria criteria);
}
