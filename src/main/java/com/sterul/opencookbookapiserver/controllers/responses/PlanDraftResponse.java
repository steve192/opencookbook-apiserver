package com.sterul.opencookbookapiserver.controllers.responses;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import com.sterul.opencookbookapiserver.entities.planning.PlanDraft;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraftSlot;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/** A proposed week. Every cooked meal carries the terms that placed its recipe there. */
public record PlanDraftResponse(Long id, Long profileId, LocalDate startDate, int days, PlanDraft.Status status,
        List<Slot> slots) {

    /**
     * @param recipe     the same shape as everywhere else a recipe is listed; null for a gap, and for a
     *                   meal the cookbook had nothing for
     * @param leftoverOf for a leftover, the slot where it is cooked
     */
    public record Slot(Long id, LocalDate date, MealType mealType, SlotKind kind, RecipeResponse recipe,
            Integer servings, Long leftoverOf, boolean locked, List<ScoreReason> reasons) {
    }

    public static PlanDraftResponse fromEntity(PlanDraft draft, Function<Recipe, RecipeResponse> recipes) {
        return new PlanDraftResponse(draft.getId(), draft.getProfile() == null ? null : draft.getProfile().getId(),
                draft.getStartDate(), draft.getDays(), draft.getStatus(),
                draft.getSlots().stream().map(slot -> slot(slot, recipes)).toList());
    }

    private static Slot slot(PlanDraftSlot slot, Function<Recipe, RecipeResponse> recipes) {
        return new Slot(slot.getId(), slot.getPlanDate(), slot.getMealType(), slot.getKind(),
                slot.getRecipe() == null ? null : recipes.apply(slot.getRecipe()), slot.getServings(),
                slot.getLeftoverOf() == null ? null : slot.getLeftoverOf().getId(), slot.isLocked(),
                slot.getTerms().stream().map(ScoreReason::of).toList());
    }
}
