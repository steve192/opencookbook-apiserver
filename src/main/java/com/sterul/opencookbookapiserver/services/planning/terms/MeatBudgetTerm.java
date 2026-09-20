package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * Keeps meat within the meals a week the cook allowed. Fish counts as meat. Once the budget is
 * spent a meat dish is still possible, for a cookbook with nothing else left, but costs heavily.
 */
@Component
public class MeatBudgetTerm implements PlanTerm {

    public static final String TERM = "meatBudget";
    private static final double OVER_BUDGET = -1.5;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        var budget = state.profile().getMeatMealsPerWeek();
        if (budget == null || !candidate.isMeat() || state.meatMeals() < budget) {
            return Optional.empty();
        }
        return Optional.of(new ScoreTerm(TERM, OVER_BUDGET));
    }
}
