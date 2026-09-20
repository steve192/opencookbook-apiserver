package com.sterul.opencookbookapiserver.entities.planning;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a schedule as "MONDAY:SIMPLE,SATURDAY:ANY", in week order. */
@Converter
@Immutable
public class MealScheduleConverter implements AttributeConverter<MealSchedule, String> {

    private static final String DAY_SEPARATOR = ",";
    private static final String EFFORT_SEPARATOR = ":";

    @Override
    public String convertToDatabaseColumn(MealSchedule schedule) {
        return schedule == null ? "" : schedule.efforts().entrySet().stream()
                .map(day -> day.getKey().name() + EFFORT_SEPARATOR + day.getValue().name())
                .collect(Collectors.joining(DAY_SEPARATOR));
    }

    @Override
    public MealSchedule convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return MealSchedule.never();
        }
        return new MealSchedule(Arrays.stream(stored.split(DAY_SEPARATOR))
                .map(day -> day.split(EFFORT_SEPARATOR))
                .collect(Collectors.toMap(day -> DayOfWeek.valueOf(day[0]), day -> Effort.valueOf(day[1]))));
    }
}
