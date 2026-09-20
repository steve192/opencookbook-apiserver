package com.sterul.opencookbookapiserver.services.planning;

import java.util.Optional;

import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * One consideration in choosing a recipe for a meal of the week. Soft by design: a term can make a
 * recipe less likely, never impossible, so a real cookbook still yields a plan with a named
 * compromise instead of an empty week. Adding a term is adding a bean.
 */
public interface PlanTerm {

    /** Empty where the cook asked nothing this term could judge. */
    Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state);
}
