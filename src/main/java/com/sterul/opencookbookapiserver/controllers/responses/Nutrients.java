package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutrientAmounts;

import jakarta.validation.constraints.PositiveOrZero;

/** Per 100 g for foods; null when unknown. */
public record Nutrients(
        @PositiveOrZero Float energyKcal,
        @PositiveOrZero Float energyKj,
        @PositiveOrZero Float fat,
        @PositiveOrZero Float saturatedFat,
        @PositiveOrZero Float carbohydrates,
        @PositiveOrZero Float sugar,
        @PositiveOrZero Float fibre,
        @PositiveOrZero Float protein,
        @PositiveOrZero Float salt) {

    /** Rounded to tenths. */
    public static Nutrients of(NutrientAmounts amounts) {
        return new Nutrients(tenths(amounts.energyKcal()), tenths(amounts.energyKj()), tenths(amounts.fat()),
                tenths(amounts.saturatedFat()), tenths(amounts.carbohydrates()), tenths(amounts.sugar()),
                tenths(amounts.fibre()), tenths(amounts.protein()), tenths(amounts.salt()));
    }

    public static Nutrients of(NutrientValues values) {
        return new Nutrients(values.getEnergyKcal(), values.getEnergyKj(), values.getFat(), values.getSaturatedFat(),
                values.getCarbohydrates(), values.getSugar(), values.getFibre(), values.getProtein(), values.getSalt());
    }

    private static Float tenths(double value) {
        return Math.round(value * 10) / 10f;
    }

    public NutrientValues toValues() {
        return NutrientValues.builder()
                .energyKcal(energyKcal)
                .energyKj(energyKj)
                .fat(fat)
                .saturatedFat(saturatedFat)
                .carbohydrates(carbohydrates)
                .sugar(sugar)
                .fibre(fibre)
                .protein(protein)
                .salt(salt)
                .build();
    }
}
