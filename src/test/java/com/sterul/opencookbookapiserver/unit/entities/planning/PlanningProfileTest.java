package com.sterul.opencookbookapiserver.unit.entities.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.planning.MealSlotSetting;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;

/** How a day's energy is shared between its meals. */
class PlanningProfileTest {

    private static MealSlotSetting meal(MealType type, Integer targetKcal) {
        return MealSlotSetting.builder().mealType(type).targetKcal(targetKcal).build();
    }

    private static PlanningProfile profile(Integer kcalPerDay, MealSlotSetting... meals) {
        return PlanningProfile.builder().name("test").kcalPerDay(kcalPerDay).meals(new ArrayList<>(List.of(meals))).build();
    }

    @Test
    void theMealsShareTheDayEvenly() {
        var lunch = meal(MealType.LUNCH, null);
        var profile = profile(1800, lunch, meal(MealType.DINNER, null));

        assertEquals(900, profile.kcalTargetOf(lunch));
    }

    /** A light lunch leaves more for dinner, not less for every meal. */
    @Test
    void aMealsOwnTargetWinsAndTheOthersShareWhatIsLeft() {
        var lunch = meal(MealType.LUNCH, 500);
        var dinner = meal(MealType.DINNER, null);
        var profile = profile(1800, lunch, dinner, meal(MealType.BREAKFAST, null));

        assertEquals(500, profile.kcalTargetOf(lunch));
        assertEquals(650, profile.kcalTargetOf(dinner));
    }

    @Test
    void withoutADailyTargetOnlyAMealsOwnCounts() {
        var lunch = meal(MealType.LUNCH, 600);
        var dinner = meal(MealType.DINNER, null);
        var profile = profile(null, lunch, dinner);

        assertEquals(600, profile.kcalTargetOf(lunch));
        assertNull(profile.kcalTargetOf(dinner));
    }
}
