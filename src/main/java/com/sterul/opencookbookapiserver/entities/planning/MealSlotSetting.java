package com.sterul.opencookbookapiserver.entities.planning;

import com.sterul.opencookbookapiserver.entities.recipe.MealType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How one meal of the day is planned. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealSlotSetting {

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 16)
    private MealType mealType;

    @Convert(converter = MealScheduleConverter.class)
    @Column(nullable = false, length = 160)
    @Builder.Default
    private MealSchedule schedule = MealSchedule.never();

    /** Per serving; null leaves it to the daily target, if any. */
    private Integer targetKcal;
}
