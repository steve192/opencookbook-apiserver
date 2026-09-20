package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;

/** Nutrient amounts in grams, energy in kcal and kJ. Unknown values count as 0. */
public record NutrientAmounts(double energyKcal, double energyKj, double fat, double saturatedFat, double carbohydrates,
        double sugar, double fibre, double protein, double salt) {

    public static final NutrientAmounts NONE = new NutrientAmounts(0, 0, 0, 0, 0, 0, 0, 0, 0);

    private static final double PER_100_G = 100;

    public static NutrientAmounts of(NutrientValues per100g, double grams) {
        var factor = grams / PER_100_G;
        return new NutrientAmounts(times(per100g.getEnergyKcal(), factor), times(per100g.getEnergyKj(), factor),
                times(per100g.getFat(), factor), times(per100g.getSaturatedFat(), factor),
                times(per100g.getCarbohydrates(), factor), times(per100g.getSugar(), factor),
                times(per100g.getFibre(), factor), times(per100g.getProtein(), factor), times(per100g.getSalt(), factor));
    }

    public NutrientAmounts plus(NutrientAmounts other) {
        return new NutrientAmounts(energyKcal + other.energyKcal, energyKj + other.energyKj, fat + other.fat,
                saturatedFat + other.saturatedFat, carbohydrates + other.carbohydrates, sugar + other.sugar,
                fibre + other.fibre, protein + other.protein, salt + other.salt);
    }

    public NutrientAmounts dividedBy(double divisor) {
        return new NutrientAmounts(energyKcal / divisor, energyKj / divisor, fat / divisor, saturatedFat / divisor,
                carbohydrates / divisor, sugar / divisor, fibre / divisor, protein / divisor, salt / divisor);
    }

    public NutrientValues toValues() {
        return NutrientValues.builder()
                .energyKcal((float) energyKcal)
                .energyKj((float) energyKj)
                .fat((float) fat)
                .saturatedFat((float) saturatedFat)
                .carbohydrates((float) carbohydrates)
                .sugar((float) sugar)
                .fibre((float) fibre)
                .protein((float) protein)
                .salt((float) salt)
                .build();
    }

    private static double times(Float value, double factor) {
        return value == null ? 0 : value * factor;
    }
}
