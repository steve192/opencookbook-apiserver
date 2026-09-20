package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.planning.Effort;
import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * Light and quick where the cook asked for simple, a proper dish where they are happy to cook.
 * Measured against the cook's own cookbook, so "simple" means the easier half of what they cook.
 */
@Component
public class EffortFitTerm implements PlanTerm {

    public static final String TERM = "effortFit";
    private static final double WEIGHT = 0.5;
    private static final double MIDDLE = 0.5;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        var wanted = slot.meal().getSchedule().effortOn(slot.date().getDayOfWeek());
        var effort = candidate.effort();
        if (wanted == Effort.ANY || effort == null) {
            return Optional.empty();
        }
        var wrongHalf = wanted == Effort.SIMPLE ? effort - MIDDLE : MIDDLE - effort;
        return Optional.of(new ScoreTerm(TERM, -WEIGHT * Math.max(0, wrongHalf) / MIDDLE));
    }
}
