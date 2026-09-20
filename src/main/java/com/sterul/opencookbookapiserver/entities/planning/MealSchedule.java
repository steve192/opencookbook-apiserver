package com.sterul.opencookbookapiserver.entities.planning;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * When one meal is cooked, and how much work it may be on each of those days, as one immutable value.
 *
 * @param efforts the weekdays it is cooked, each with its effort; on the others it is a gap
 */
public record MealSchedule(Map<DayOfWeek, Effort> efforts) {

    public MealSchedule {
        var copy = new EnumMap<DayOfWeek, Effort>(DayOfWeek.class);
        copy.putAll(efforts);
        efforts = Collections.unmodifiableMap(copy);
    }

    public static MealSchedule never() {
        return new MealSchedule(Map.of());
    }

    public static MealSchedule everyDay() {
        var efforts = new EnumMap<DayOfWeek, Effort>(DayOfWeek.class);
        for (var day : DayOfWeek.values()) {
            efforts.put(day, Effort.ANY);
        }
        return new MealSchedule(efforts);
    }

    public boolean isCookedOn(DayOfWeek day) {
        return efforts.containsKey(day);
    }

    /** {@link Effort#ANY} on a day it is not cooked. */
    public Effort effortOn(DayOfWeek day) {
        return efforts.getOrDefault(day, Effort.ANY);
    }
}
