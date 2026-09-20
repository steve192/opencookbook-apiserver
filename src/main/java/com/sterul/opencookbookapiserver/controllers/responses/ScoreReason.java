package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.entities.planning.PlanSlotTerm;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** Why a recipe was chosen, as a key and its weight; the client writes the sentence in the reader's language. */
public record ScoreReason(String term, double value) {

    public static ScoreReason of(ScoreTerm term) {
        return new ScoreReason(term.term(), term.value());
    }

    public static ScoreReason of(PlanSlotTerm term) {
        return new ScoreReason(term.getTerm(), term.getValue());
    }
}
