package com.sterul.opencookbookapiserver.services.planning;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.planning.MealSlotSetting;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;

/** Lays a period out into meals: cooked on the weekdays the cook chose for each, gaps on the others. */
@Component
public class SlotLayout {

    /** @param skipped days the cook is not at home at all; neither cooked nor gaps */
    public List<PlanSlot> layOut(LocalDate start, int days, Collection<LocalDate> skipped, List<MealSlotSetting> meals) {
        var inDayOrder = meals.stream().sorted(Comparator.comparing(MealSlotSetting::getMealType)).toList();
        return start.datesUntil(start.plusDays(days))
                .filter(date -> !skipped.contains(date))
                .flatMap(date -> inDayOrder.stream().map(meal -> new PlanSlot(date, meal,
                        meal.getSchedule().isCookedOn(date.getDayOfWeek()) ? SlotKind.COOKED : SlotKind.GAP)))
                .toList();
    }
}
