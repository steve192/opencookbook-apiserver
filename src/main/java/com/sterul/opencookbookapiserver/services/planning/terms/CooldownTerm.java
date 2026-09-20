package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * Keeps a recipe from coming round again too soon: not twice in one plan, and not within the
 * cooldown of the weekplans already saved. Strong enough that only a cookbook with nothing else
 * left falls back on a repeat - and then the draft says so.
 */
@Component
public class CooldownTerm implements PlanTerm {

    public static final String TERM = "cooldown";
    private static final double ALREADY_IN_PLAN = -3.0;
    private static final double PLANNED_RECENTLY = -2.0;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        if (state.isInPlan(candidate.id())) {
            return Optional.of(new ScoreTerm(TERM, ALREADY_IN_PLAN));
        }
        if (state.profile().getCooldownWeeks() > 0 && state.wasPlannedRecently(candidate.id())) {
            return Optional.of(new ScoreTerm(TERM, PLANNED_RECENTLY));
        }
        return Optional.empty();
    }
}
