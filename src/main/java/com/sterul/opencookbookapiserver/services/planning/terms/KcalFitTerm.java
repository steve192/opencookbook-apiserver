package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.NutritionFit;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** Near the energy the cook wants for this meal; see {@link com.sterul.opencookbookapiserver.entities.planning.PlanningProfile#kcalTargetOf}. */
@Component
public class KcalFitTerm implements PlanTerm {

    public static final String TERM = "kcalFit";
    private static final double WEIGHT = 0.5;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        return Optional.ofNullable(state.profile().kcalTargetOf(slot.meal()))
                .flatMap(target -> NutritionFit.kcal(candidate.nutrition(), target))
                .map(fit -> new ScoreTerm(TERM, WEIGHT * fit));
    }
}
