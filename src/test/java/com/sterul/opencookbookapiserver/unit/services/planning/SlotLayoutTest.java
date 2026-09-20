package com.sterul.opencookbookapiserver.unit.services.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.planning.Effort;
import com.sterul.opencookbookapiserver.entities.planning.MealSchedule;
import com.sterul.opencookbookapiserver.entities.planning.MealSlotSetting;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.services.planning.SlotLayout;

/** Which meals of a period are cooked and which are left to the cook. */
class SlotLayoutTest {

    /** A Monday, so the week reads Monday to Sunday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    private final SlotLayout cut = new SlotLayout();

    private static MealSlotSetting meal(MealType type, DayOfWeek... days) {
        var efforts = new EnumMap<DayOfWeek, Effort>(DayOfWeek.class);
        List.of(days).forEach(day -> efforts.put(day, Effort.ANY));
        return MealSlotSetting.builder().mealType(type).schedule(new MealSchedule(efforts)).build();
    }

    private List<DayOfWeek> cookedDays(MealSlotSetting meal) {
        return cut.layOut(MONDAY, 7, Set.of(), List.of(meal)).stream()
                .filter(slot -> slot.kind() == SlotKind.COOKED)
                .map(slot -> slot.date().getDayOfWeek())
                .toList();
    }

    @Test
    void aMealIsCookedOnTheDaysChosenForIt() {
        assertEquals(List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                cookedDays(meal(MealType.LUNCH, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)));
    }

    @Test
    void aMealWithNoDaysIsAllGaps() {
        var slots = cut.layOut(MONDAY, 7, Set.of(), List.of(meal(MealType.BREAKFAST)));

        assertEquals(7, slots.size());
        assertEquals(List.of(), slots.stream().filter(slot -> slot.kind() == SlotKind.COOKED).toList());
    }

    @Test
    void aSkippedDayIsNeitherCookedNorAGap() {
        var friday = MONDAY.plusDays(4);
        var slots = cut.layOut(MONDAY, 7, Set.of(friday), List.of(meal(MealType.DINNER, DayOfWeek.values())));

        assertEquals(6, slots.size());
        assertEquals(List.of(), slots.stream().filter(slot -> slot.date().equals(friday)).toList());
    }

    @Test
    void aLongerPeriodRepeatsTheWeek() {
        var slots = cut.layOut(MONDAY, 14, Set.of(), List.of(meal(MealType.DINNER, DayOfWeek.WEDNESDAY)));

        assertEquals(2, slots.stream().filter(slot -> slot.kind() == SlotKind.COOKED).count());
    }

    @Test
    void slotsComeInTheOrderOfTheDay() {
        var slots = cut.layOut(MONDAY, 1, Set.of(),
                List.of(meal(MealType.DINNER, DayOfWeek.MONDAY), meal(MealType.BREAKFAST, DayOfWeek.MONDAY)));

        assertEquals(List.of(MealType.BREAKFAST, MealType.DINNER),
                slots.stream().map(slot -> slot.meal().getMealType()).toList());
    }
}
