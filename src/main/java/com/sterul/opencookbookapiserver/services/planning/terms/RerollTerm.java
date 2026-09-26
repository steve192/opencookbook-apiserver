package com.sterul.opencookbookapiserver.services.planning.terms;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanSlot;
import com.sterul.opencookbookapiserver.services.planning.PlanState;
import com.sterul.opencookbookapiserver.services.planning.PlanTerm;
import com.sterul.opencookbookapiserver.services.planning.RerollReason;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/** Keeps a replacement away from what put the cook off the recipe they passed over. */
@Component
public class RerollTerm implements PlanTerm {

    public static final String TERM = "reroll";
    private static final double WEIGHT = 1.0;
    private static final double SAME_GROUP = 0.5;
    /** A recipe without meal types is not known to be a dish, so one with them wins over it. */
    private static final double UNKNOWN_ROLE = 0.5;

    @Override
    public Optional<ScoreTerm> score(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        var reroll = slot.reroll();
        if (reroll == null || reroll.reason() == null) {
            return Optional.empty();
        }
        // A recipe no longer in the pool cannot be compared with; it is only excluded
        return state.candidate(reroll.rejectedRecipeId())
                .map(rejected -> likeness(candidate, rejected, reroll.reason()))
                .filter(likeness -> likeness > 0)
                .map(likeness -> new ScoreTerm(TERM, -WEIGHT * likeness));
    }

    /** From 0, nothing like the recipe passed over in the way that put the cook off, to 1. */
    private static double likeness(PlanCandidate candidate, PlanCandidate rejected, RerollReason reason) {
        return switch (reason) {
            case HAD_RECENTLY -> hadRecentlyLikeness(candidate, rejected);
            case TOO_MUCH_WORK -> candidate.effort() != null && rejected.effort() != null
                    && candidate.effort() >= rejected.effort() ? 1 : 0;
            case MISSING_INGREDIENTS -> candidate.shareOfIngredientsOf(rejected);
            // Sauces sit with sauces: the rejected recipe's group is the best hint of what else is no dish
            case NOT_A_FULL_MEAL -> notAFullMealLikeness(candidate, rejected);
        };
    }

    private static double hadRecentlyLikeness(PlanCandidate candidate, PlanCandidate rejected) {
        if (candidate.sharesMainFood(rejected)) {
            return 1;
        }
        return candidate.sharesGroup(rejected) ? SAME_GROUP : 0;
    }

    private static double notAFullMealLikeness(PlanCandidate candidate, PlanCandidate rejected) {
        if (candidate.sharesGroup(rejected)) {
            return 1;
        }
        // A recipe that is not known to be a dish may still be one, so it is only half ruled out.
        return candidate.isKnownDish() ? 0 : UNKNOWN_ROLE;
    }
}
