package com.sterul.opencookbookapiserver.controllers.requests;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param days         defaults to a week
 * @param skippedDates days the cook is not at home; nothing is planned for them
 */
public record PlanDraftRequest(@NotNull Long profileId, @NotNull LocalDate startDate, @Min(1) @Max(14) Integer days,
        @Size(max = 14) Set<LocalDate> skippedDates) {

    private static final int A_WEEK = 7;

    public int daysOrWeek() {
        return days == null ? A_WEEK : days;
    }

    public Set<LocalDate> skippedOrNone() {
        return skippedDates == null ? Set.of() : skippedDates;
    }
}
