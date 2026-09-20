package com.sterul.opencookbookapiserver.unit.services.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.planning.Effort;
import com.sterul.opencookbookapiserver.entities.planning.MealSchedule;
import com.sterul.opencookbookapiserver.entities.planning.MealSlotSetting;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraftSlot;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.services.planning.PantryBudget;
import com.sterul.opencookbookapiserver.services.planning.PlanCandidate;
import com.sterul.opencookbookapiserver.services.planning.PlanningPool;
import com.sterul.opencookbookapiserver.services.planning.Reroll;
import com.sterul.opencookbookapiserver.services.planning.RerollReason;
import com.sterul.opencookbookapiserver.services.planning.WeekplanGenerator;
import com.sterul.opencookbookapiserver.services.planning.terms.CooldownTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.EffortFitTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.JitterTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.MealTypeFitTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.MeatBudgetTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.PantryTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.RerollTerm;
import com.sterul.opencookbookapiserver.services.planning.terms.VarietyTerm;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;

/**
 * Choosing recipes for a week, with the terms that do not need nutrition. What is pinned here is
 * what makes a generated week feel planned rather than generated: no repeats, the budgets kept,
 * the pantry used up once and then left alone, gaps left alone, leftovers where a recipe makes them.
 */
class WeekplanGeneratorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    private final WeekplanGenerator cut = new WeekplanGenerator(List.of(new CooldownTerm(), new MealTypeFitTerm(),
            new EffortFitTerm(), new MeatBudgetTerm(), new VarietyTerm(), new PantryTerm(),
            new RerollTerm(), new JitterTerm()));

    private static Recipe recipe(long id, Diet diet, long minutes, int servings) {
        return Recipe.builder().id(id).title("Rezept " + id).recipeType(diet).totalTime(minutes).servings(servings).build();
    }

    private static PlanCandidate candidate(Recipe recipe) {
        return new PlanCandidate(recipe, null, null, Map.of());
    }

    private static PlanningPool.Pool pool(List<PlanCandidate> candidates) {
        return new PlanningPool.Pool(candidates, PantryBudget.EMPTY);
    }

    private static PlanningProfile profile(MealSlotSetting... meals) {
        return PlanningProfile.builder().name("test").householdSize(2).leftoversAllowed(false)
                .meals(new ArrayList<>(List.of(meals))).build();
    }

    private static MealSlotSetting dinner() {
        return MealSlotSetting.builder().mealType(MealType.DINNER).schedule(MealSchedule.everyDay()).build();
    }

    private static List<PlanDraftSlot> week(MealType meal, SlotKind kind) {
        return IntStream.range(0, 7)
                .mapToObj(day -> PlanDraftSlot.builder().id((long) day + 1).planDate(MONDAY.plusDays(day))
                        .mealType(meal).kind(kind).build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<PlanDraftSlot> fill(List<PlanDraftSlot> board, PlanningProfile profile, PlanningPool.Pool pool) {
        cut.fill(board, new HashSet<>(board), profile, pool, Set.of(), Map.of(), 42L);
        return board;
    }

    private static List<Long> recipeIds(List<PlanDraftSlot> board) {
        return board.stream().map(slot -> slot.getRecipe() == null ? null : slot.getRecipe().getId()).toList();
    }

    private static List<PlanCandidate> recipes(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> candidate(recipe(id, Diet.VEGETARIAN, 30, 2)))
                .toList();
    }

    @Test
    void aWeekHasNoRepeatsWhileTheCookbookLasts() {
        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile(dinner()), pool(recipes(10)));

        assertEquals(7, recipeIds(board).stream().filter(Objects::nonNull).distinct().count());
    }

    /** A cookbook too small for the week repeats rather than leaving meals empty. */
    @Test
    void aSmallCookbookRepeatsRatherThanLeavingMealsOut() {
        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile(dinner()), pool(recipes(3)));

        assertTrue(recipeIds(board).stream().allMatch(Objects::nonNull));
    }

    @Test
    void gapsAreLeftToTheCook() {
        var board = fill(week(MealType.BREAKFAST, SlotKind.GAP),
                profile(MealSlotSetting.builder().mealType(MealType.BREAKFAST).build()), pool(recipes(10)));

        assertTrue(board.stream().allMatch(slot -> slot.getKind() == SlotKind.GAP && slot.getRecipe() == null));
    }

    @Test
    void theMeatBudgetIsKeptWhileThereIsSomethingElse() {
        var candidates = new ArrayList<PlanCandidate>();
        IntStream.rangeClosed(1, 7).forEach(id -> candidates.add(candidate(recipe(id, Diet.MEAT, 30, 2))));
        IntStream.rangeClosed(8, 14).forEach(id -> candidates.add(candidate(recipe(id, Diet.VEGAN, 30, 2))));
        var profile = profile(dinner());
        profile.setMeatMealsPerWeek(2);

        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile, pool(candidates));

        assertTrue(board.stream().filter(slot -> slot.getRecipe().getRecipeType() == Diet.MEAT).count() <= 2);
    }

    /** Quick on the days the cook asked for quick, whatever the other days allow. */
    @Test
    void eachDayGetsTheEffortChosenForIt() {
        var quick = IntStream.rangeClosed(1, 7).mapToObj(id -> new PlanCandidate(recipe(id, null, 20, 2), null, 0.0, Map.of()));
        var elaborate = IntStream.rangeClosed(8, 14).mapToObj(id -> new PlanCandidate(recipe(id, null, 120, 2), null, 1.0, Map.of()));
        var efforts = new EnumMap<DayOfWeek, Effort>(DayOfWeek.class);
        EnumSet.allOf(DayOfWeek.class).forEach(day -> efforts.put(day, day.getValue() <= 5 ? Effort.SIMPLE : Effort.ANY));
        var profile = profile(MealSlotSetting.builder().mealType(MealType.DINNER).schedule(new MealSchedule(efforts)).build());

        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile,
                pool(java.util.stream.Stream.concat(quick, elaborate).toList()));

        assertTrue(board.subList(0, 5).stream().allMatch(slot -> slot.getRecipe().getTotalTime() == 20));
    }

    /**
     * What the cook has is used up - and then left alone. The courgette recipe earns the pantry
     * bonus once; with the courgettes gone, a second courgette recipe earns nothing for them.
     */
    @Test
    void thePantryIsUsedUpOnceAndThenLeftAlone() {
        var courgette = new MatchTarget(100L, "Zucchini", Set.of());
        var pantry = new PantryBudget(Map.of(courgette, 500.0), Set.of(courgette));
        var candidates = new ArrayList<PlanCandidate>();
        IntStream.rangeClosed(1, 3).forEach(id -> candidates.add(
                new PlanCandidate(recipe(id, null, 30, 2), null, null, Map.of(courgette, 500.0))));
        IntStream.rangeClosed(4, 10).forEach(id -> candidates.add(candidate(recipe(id, null, 30, 2))));

        var board = week(MealType.DINNER, SlotKind.COOKED);
        cut.fill(board, new HashSet<>(board), profile(dinner()), new PlanningPool.Pool(candidates, pantry),
                Set.of(), Map.of(), 42L);

        var courgetteDishes = board.stream().filter(slot -> slot.getRecipe().getId() <= 3).count();
        assertEquals(1, courgetteDishes);
        assertTrue(board.get(0).getRecipe().getId() <= 3, "the stock is used on the first day it can be");
    }

    /** A recipe that makes enough is eaten again the next day, and cooked for both meals. */
    @Test
    void aRecipeThatMakesEnoughLeavesLeftoversForTheNextDay() {
        var profile = profile(dinner());
        profile.setLeftoversAllowed(true);
        var bigPot = IntStream.rangeClosed(1, 10).mapToObj(id -> candidate(recipe(id, null, 60, 6))).toList();

        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile, pool(bigPot));

        var monday = board.get(0);
        var tuesday = board.get(1);
        assertEquals(SlotKind.COOKED, monday.getKind());
        assertEquals(4, monday.getServings());
        assertEquals(SlotKind.LEFTOVER, tuesday.getKind());
        assertEquals(monday.getRecipe(), tuesday.getRecipe());
        assertEquals(monday, tuesday.getLeftoverOf());
    }

    @Test
    void aRecipeForTheHouseholdOnlyLeavesNoLeftovers() {
        var profile = profile(dinner());
        profile.setLeftoversAllowed(true);

        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile, pool(recipes(10)));

        assertTrue(board.stream().noneMatch(slot -> slot.getKind() == SlotKind.LEFTOVER));
    }

    @Test
    void aRerollNeverGivesBackTheRecipeItReplaces() {
        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile(dinner()), pool(recipes(10)));
        var wednesday = board.get(2);
        var before = wednesday.getRecipe().getId();

        cut.fill(board, Set.of(wednesday), profile(dinner()), pool(recipes(10)), Set.of(),
                Map.of(wednesday, new Reroll(before, null)), 43L);

        assertNotEquals(before, wednesday.getRecipe().getId());
        assertEquals(7, recipeIds(board).stream().distinct().count(), "the reroll takes the rest of the week into account");
    }

    /** "Too much work" gets a replacement that is less work, not merely a different one. */
    @Test
    void aRerollForTooMuchWorkGetsSomethingEasier() {
        var candidates = IntStream.rangeClosed(1, 10)
                .mapToObj(id -> new PlanCandidate(recipe(id, null, 30, 2), null, id / 10.0, Map.of()))
                .toList();
        var board = week(MealType.DINNER, SlotKind.COOKED);
        var wednesday = board.get(2);
        // The second easiest, so exactly one recipe is easier still
        var passedOver = candidates.get(1).recipe();
        wednesday.setRecipe(passedOver);

        cut.fill(board, Set.of(wednesday), profile(dinner()), pool(candidates), Set.of(),
                Map.of(wednesday, new Reroll(passedOver.getId(), RerollReason.TOO_MUCH_WORK)), 43L);

        assertEquals(candidates.get(0).recipe(), wednesday.getRecipe());
    }

    /** "Not a full meal" prefers a recipe known to be a dish, and keeps away from the rejected one's group. */
    @Test
    void aRerollForNotAFullMealGetsADish() {
        var sauces = RecipeGroup.builder().id(1L).title("Saucen").build();
        var candidates = IntStream.rangeClosed(1, 10).mapToObj(id -> {
            var recipe = recipe(id, null, 30, 2);
            recipe.setRecipeGroups(new ArrayList<>(id <= 5 ? List.of(sauces) : List.of()));
            recipe.setMealTypes(id == 10 ? new HashSet<>(Set.of(MealType.DINNER)) : new HashSet<>());
            return candidate(recipe);
        }).toList();
        var board = week(MealType.DINNER, SlotKind.COOKED);
        var wednesday = board.get(2);
        var sauce = candidates.get(0).recipe();
        wednesday.setRecipe(sauce);

        cut.fill(board, Set.of(wednesday), profile(dinner()), pool(candidates), Set.of(),
                Map.of(wednesday, new Reroll(sauce.getId(), RerollReason.NOT_A_FULL_MEAL)), 43L);

        assertEquals(candidates.get(9).recipe(), wednesday.getRecipe());
    }

    @Test
    void oneSeedAlwaysGivesOneWeek() {
        var first = recipeIds(fill(week(MealType.DINNER, SlotKind.COOKED), profile(dinner()), pool(recipes(20))));
        var second = recipeIds(fill(week(MealType.DINNER, SlotKind.COOKED), profile(dinner()), pool(recipes(20))));

        assertEquals(first, second);
    }

    @Test
    void everyChoiceKeepsTheTermsThatMadeIt() {
        var board = fill(week(MealType.DINNER, SlotKind.COOKED), profile(dinner()), pool(recipes(10)));

        assertTrue(board.stream().allMatch(slot -> !slot.getTerms().isEmpty()));
    }
}
