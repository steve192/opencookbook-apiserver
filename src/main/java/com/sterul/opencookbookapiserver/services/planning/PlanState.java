package com.sterul.opencookbookapiserver.services.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;

/**
 * The plan as it stands while it is being filled: what each scoring term has to look at to judge
 * the next recipe against the ones already chosen.
 */
public final class PlanState {

    private final PlanningProfile profile;
    private final PantryBudget pantry;
    private final Map<MatchTarget, Double> pantryLeft;
    private final Set<Long> recentlyPlanned;
    private final Map<Long, PlanCandidate> candidates;
    private final Map<LocalDate, List<PlanCandidate>> cookedByDate = new HashMap<>();
    private final Set<Long> inPlan = new HashSet<>();
    private final long seed;
    private int meatMeals;

    /**
     * @param recentlyPlanned recipes already in the saved weekplan within the cooldown
     * @param candidates      the pool, by recipe id
     */
    public PlanState(PlanningProfile profile, PantryBudget pantry, Set<Long> recentlyPlanned,
            Map<Long, PlanCandidate> candidates, long seed) {
        this.profile = profile;
        this.pantry = pantry;
        this.pantryLeft = pantry.fresh();
        this.recentlyPlanned = Set.copyOf(recentlyPlanned);
        this.candidates = Map.copyOf(candidates);
        this.seed = seed;
    }

    /** A recipe cooked on a day: it counts for variety, the meat budget and the pantry. */
    public void cooked(LocalDate date, PlanCandidate candidate) {
        cookedByDate.computeIfAbsent(date, day -> new ArrayList<>()).add(candidate);
        inPlan.add(candidate.id());
        if (candidate.isMeat()) {
            meatMeals++;
        }
        candidate.pantryUse().forEach((target, use) -> pantryLeft.computeIfPresent(target,
                (item, left) -> Math.max(0, left - use)));
    }

    public PlanningProfile profile() {
        return profile;
    }

    public PantryBudget pantry() {
        return pantry;
    }

    public double pantryLeft(MatchTarget target) {
        return pantryLeft.getOrDefault(target, 0.0);
    }

    public boolean isInPlan(Long recipeId) {
        return inPlan.contains(recipeId);
    }

    public boolean wasPlannedRecently(Long recipeId) {
        return recentlyPlanned.contains(recipeId);
    }

    /** Empty for a recipe no longer in the pool, say after its diet changed. */
    public Optional<PlanCandidate> candidate(Long recipeId) {
        return Optional.ofNullable(candidates.get(recipeId));
    }

    public int meatMeals() {
        return meatMeals;
    }

    /** What is cooked on the day and the days either side of it. */
    public List<PlanCandidate> cookedAround(LocalDate date) {
        var around = new ArrayList<PlanCandidate>();
        for (var day = date.minusDays(1); !day.isAfter(date.plusDays(1)); day = day.plusDays(1)) {
            around.addAll(cookedByDate.getOrDefault(day, List.of()));
        }
        return around;
    }

    public long seed() {
        return seed;
    }
}
