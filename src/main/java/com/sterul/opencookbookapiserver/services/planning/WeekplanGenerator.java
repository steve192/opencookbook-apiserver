package com.sterul.opencookbookapiserver.services.planning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.planning.PlanDraftSlot;
import com.sterul.opencookbookapiserver.entities.planning.PlanSlotTerm;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.services.selection.RecipeSuitability;
import com.sterul.opencookbookapiserver.services.selection.ScoreTerm;

/**
 * Chooses recipes for the meals of a week, greedily day by day: most of what makes a week good depends
 * on what came before, and every choice keeps the terms that explain it. Fills only the given slots and
 * takes the rest of the board as it stands, so generating, rerolling and refilling share one path.
 */
@Component
public class WeekplanGenerator {

    /** Leftovers make sense as a later lunch or dinner, not as tomorrow's breakfast or dessert. */
    private static final Set<MealType> TAKES_LEFTOVERS = EnumSet.of(MealType.LUNCH, MealType.DINNER);

    private final List<PlanTerm> terms;

    public WeekplanGenerator(List<PlanTerm> terms) {
        this.terms = terms;
    }

    /**
     * @param toFill   the slots to choose for; a gap among them stays a gap, a leftover is decided again
     * @param rerolls  per slot, the recipe it must not get again and why it was passed over
     */
    public void fill(List<PlanDraftSlot> board, Collection<PlanDraftSlot> toFill, PlanningProfile profile,
            PlanningPool.Pool pool, Set<Long> recentlyPlanned, Map<PlanDraftSlot, Reroll> rerolls, long seed) {
        // By identity: slots of a draft not yet saved all have a null id, and slot equality is by id.
        var fill = identitySet(toFill);
        var ordered = board.stream().sorted(PlanDraftSlot.DAY_ORDER).toList();
        var byId = pool.candidates().stream().collect(Collectors.toMap(PlanCandidate::id, Function.identity()));

        var state = new PlanState(profile, pool.pantry(), recentlyPlanned, byId, seed);
        ordered.stream()
                .filter(slot -> !fill.contains(slot) && slot.getKind() == SlotKind.COOKED && slot.getRecipe() != null)
                .forEach(slot -> Optional.ofNullable(byId.get(slot.getRecipe().getId()))
                        .ifPresent(candidate -> state.cooked(slot.getPlanDate(), candidate)));
        assign(ordered, fill, pool, state, rerolls).forEach(WeekplanGenerator::apply);
    }

    /** What one slot gets. */
    private record Assignment(SlotKind kind, PlanCandidate candidate, Integer servings, PlanDraftSlot leftoverOf,
            List<ScoreTerm> terms) {

        static final Assignment GAP = new Assignment(SlotKind.GAP, null, null, null, List.of());
        /** A meal to cook that the cookbook has nothing for; the draft says so rather than inventing one. */
        static final Assignment NOTHING_FITS = new Assignment(SlotKind.COOKED, null, null, null, List.of());
    }

    private record Scored(PlanCandidate candidate, List<ScoreTerm> terms, double score) {
    }

    private Map<PlanDraftSlot, Assignment> assign(List<PlanDraftSlot> ordered, Set<PlanDraftSlot> toFill,
            PlanningPool.Pool pool, PlanState state, Map<PlanDraftSlot, Reroll> rerolls) {
        var assignments = new IdentityHashMap<PlanDraftSlot, Assignment>();
        var household = state.profile().getHouseholdSize();
        for (var slot : ordered) {
            if (!toFill.contains(slot) || assignments.containsKey(slot)) {
                continue;
            }
            if (slot.getKind() == SlotKind.GAP) {
                assignments.put(slot, Assignment.GAP);
                continue;
            }
            var reroll = rerolls.get(slot);
            var planSlot = new PlanSlot(slot.getPlanDate(), state.profile().mealSetting(slot.getMealType()), SlotKind.COOKED,
                    reroll);
            var best = best(planSlot, pool, state, reroll == null ? null : reroll.rejectedRecipeId());
            if (best.isEmpty()) {
                assignments.put(slot, Assignment.NOTHING_FITS);
                continue;
            }
            var chosen = best.get();
            state.cooked(slot.getPlanDate(), chosen.candidate());
            var leftover = state.profile().isLeftoversAllowed() ?
                    leftoverSlot(ordered, toFill, assignments, slot, chosen.candidate(), household) :
                    Optional.<PlanDraftSlot>empty();
            var cookedServings = leftover.isPresent() ? 2 * household : household;
            assignments.put(slot, new Assignment(SlotKind.COOKED, chosen.candidate(), cookedServings, null, chosen.terms()));
            leftover.ifPresent(later -> assignments.put(later,
                    new Assignment(SlotKind.LEFTOVER, chosen.candidate(), household, slot, List.of())));
        }
        return assignments;
    }

    private Optional<Scored> best(PlanSlot slot, PlanningPool.Pool pool, PlanState state, Long excludedRecipeId) {
        var meal = Set.of(slot.meal().getMealType());
        return pool.candidates().stream()
                .filter(candidate -> !candidate.id().equals(excludedRecipeId))
                .filter(candidate -> RecipeSuitability.suitsMeals(candidate.recipe(), meal))
                .map(candidate -> scored(candidate, slot, state))
                .max(Comparator.comparingDouble(Scored::score).thenComparing(scored -> -scored.candidate().id()));
    }

    private Scored scored(PlanCandidate candidate, PlanSlot slot, PlanState state) {
        var scoreTerms = terms.stream()
                .map(term -> term.score(candidate, slot, state))
                .flatMap(Optional::stream)
                .toList();
        return new Scored(candidate, scoreTerms, ScoreTerm.total(scoreTerms));
    }

    /**
     * Where a recipe cooked for enough people can be eaten again: the next day's first lunch or
     * dinner still open. Only when the recipe really makes that much, so leftovers are what the
     * recipe yields rather than an excuse to cook less.
     */
    private static Optional<PlanDraftSlot> leftoverSlot(List<PlanDraftSlot> ordered, Set<PlanDraftSlot> toFill,
            Map<PlanDraftSlot, Assignment> assignments, PlanDraftSlot cooked, PlanCandidate candidate, int household) {
        if (candidate.recipe().getServings() < 2 * household) {
            return Optional.empty();
        }
        var nextDay = cooked.getPlanDate().plusDays(1);
        return ordered.stream()
                .filter(slot -> slot.getPlanDate().equals(nextDay))
                .filter(slot -> TAKES_LEFTOVERS.contains(slot.getMealType()))
                .filter(slot -> toFill.contains(slot) && slot.getKind() != SlotKind.GAP && !slot.isLocked())
                .filter(slot -> !assignments.containsKey(slot))
                .findFirst();
    }

    private static Set<PlanDraftSlot> identitySet(Collection<PlanDraftSlot> slots) {
        var set = Collections.newSetFromMap(new IdentityHashMap<PlanDraftSlot, Boolean>());
        set.addAll(slots);
        return set;
    }

    private static void apply(PlanDraftSlot slot, Assignment assignment) {
        slot.setKind(assignment.kind());
        slot.setRecipe(assignment.candidate() == null ? null : assignment.candidate().recipe());
        slot.setServings(assignment.servings());
        slot.setLeftoverOf(assignment.leftoverOf());
        slot.setTerms(new ArrayList<>(assignment.terms().stream()
                .map(term -> new PlanSlotTerm(term.term(), term.value()))
                .toList()));
    }
}
