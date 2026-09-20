package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.RecipeSuitability;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** Prefers a recipe its owner marked as this meal over one nobody said anything about. */
@Component
public class MealTypeFitTerm implements PlanTerm {

    public static final String TERM = "mealTypeFit";
    private static final double WEIGHT = 0.3;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        return RecipeSuitability.isMarkedFor(candidate.recipe(), Set.of(slot.meal().getMealType())) ?
                Optional.of(new ScoreTerm(TERM, WEIGHT)) :
                Optional.empty();
    }
}
