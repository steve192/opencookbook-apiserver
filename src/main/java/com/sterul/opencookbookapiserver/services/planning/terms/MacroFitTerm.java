package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.selection.NutritionFit;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** Low carb, low fat or high protein, judged from the stored nutrition of each recipe. */
@Component
public class MacroFitTerm implements PlanTerm {

    public static final String TERM = "macroFit";
    private static final double WEIGHT = 0.3;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        return NutritionFit.macros(candidate.nutrition(), state.profile().getMacroStyle())
                .map(fit -> new ScoreTerm(TERM, WEIGHT * fit));
    }
}
