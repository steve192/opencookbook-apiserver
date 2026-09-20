package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * Keeps neighbouring days apart: not the same main food two days running - three pasta dishes in a
 * row is what makes a generated plan feel generated - and, more mildly, not the same recipe group.
 */
@Component
public class VarietyTerm implements PlanTerm {

    public static final String TERM = "variety";
    private static final double SAME_MAIN_FOOD = -0.4;
    private static final double SAME_GROUP = -0.2;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        if (!state.profile().isSpreadVariety()) {
            return Optional.empty();
        }
        var penalty = 0.0;
        for (var neighbour : state.cookedAround(slot.date())) {
            if (candidate.sharesMainFood(neighbour)) {
                penalty += SAME_MAIN_FOOD;
            } else if (candidate.sharesGroup(neighbour)) {
                penalty += SAME_GROUP;
            }
        }
        return penalty == 0 ? Optional.empty() : Optional.of(new ScoreTerm(TERM, penalty));
    }
}
