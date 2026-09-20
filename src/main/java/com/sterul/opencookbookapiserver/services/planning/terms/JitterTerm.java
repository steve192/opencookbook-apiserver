package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.Jitter;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** Keeps equally good recipes from always landing in the same meal; see {@link Jitter}. */
@Component
public class JitterTerm implements PlanTerm {

    public static final String TERM = "jitter";
    private static final double WEIGHT = 0.05;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        var draw = Jitter.draw(state.seed(), candidate.id(), slot.date(), slot.meal().getMealType());
        return Optional.of(new ScoreTerm(TERM, WEIGHT * draw));
    }
}
