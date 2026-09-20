package com.sterul.opencookbookapiserver.services.planning;

import java.time.LocalDate;

import com.sterul.opencookbookapiserver.entities.planning.MealSlotSetting;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;

/**
 * One meal of the period, before a recipe is chosen for it.
 *
 * @param reroll what the cook passed over here, and why; null otherwise
 */
public record PlanSlot(LocalDate date, MealSlotSetting meal, SlotKind kind, Reroll reroll) {

    public PlanSlot(LocalDate date, MealSlotSetting meal, SlotKind kind) {
        this(date, meal, kind, null);
    }
}
