package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * Uses up what the cook has, then keeps it off the rest of the week: a recipe earns the share of the
 * stock it uses, and once the stock is gone another use costs a little.
 */
@Component
public class PantryTerm implements PlanTerm {

    public static final String TERM = "pantry";
    private static final double WEIGHT = 1.0;
    private static final double USED_UP_AGAIN = -0.3;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        var pantry = state.pantry();
        if (pantry.isEmpty() || candidate.pantryUse().isEmpty()) {
            return Optional.empty();
        }
        var value = 0.0;
        for (var use : candidate.pantryUse().entrySet()) {
            var initial = pantry.initial().getOrDefault(use.getKey(), 0.0);
            if (initial <= 0) {
                continue;
            }
            var left = state.pantryLeft(use.getKey());
            value += left > 0 ? WEIGHT * Math.min(use.getValue(), left) / initial : USED_UP_AGAIN;
        }
        return value == 0 ? Optional.empty() : Optional.of(new ScoreTerm(TERM, value));
    }
}
