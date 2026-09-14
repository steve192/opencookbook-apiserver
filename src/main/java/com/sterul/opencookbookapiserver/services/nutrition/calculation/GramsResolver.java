package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/**
 * Converts a line's amount and unit into grams of its food. Variants without own portions or density use
 * their base's; a line without a unit counts pieces.
 */
@Component
public class GramsResolver {

    private static final double WATER_DENSITY = 1.0;

    private final UnitLexicon unitLexicon;

    public GramsResolver(UnitLexicon unitLexicon) {
        this.unitLexicon = unitLexicon;
    }

    /** @param ownPortions the owner's piece weights in grams, by unit key */
    public AmountInGrams resolve(Float amount, String unitWord, CatalogueFood food, Map<String, Float> ownPortions) {
        var unit = unitLexicon.resolveLineUnit(unitWord);
        if (unit.isEmpty()) {
            return AmountInGrams.unresolved(LineStatus.UNIT_UNKNOWN);
        }
        return switch (unit.get().kind()) {
            case PINCH -> AmountInGrams.resolved(count(amount) * unit.get().grams(), EnumSet.of(LineFlag.PINCH));
            case VAGUE -> AmountInGrams.unresolved(LineStatus.NO_AMOUNT);
            case MASS -> amount == null ? AmountInGrams.unresolved(LineStatus.NO_AMOUNT)
                    : AmountInGrams.resolved(amount * unit.get().grams(), EnumSet.noneOf(LineFlag.class));
            case VOLUME -> amount == null ? AmountInGrams.unresolved(LineStatus.NO_AMOUNT) : volume(amount, unit.get(), food);
            case COUNT -> amount == null ? AmountInGrams.unresolved(LineStatus.NO_AMOUNT) : pieces(amount, unit.get(), food, ownPortions);
        };
    }

    /** Grams independent of the food (mass, volume as water, pinch); empty otherwise. */
    public Optional<Double> weightWithoutFood(Float amount, String unitWord) {
        return unitLexicon.resolveLineUnit(unitWord).flatMap(unit -> switch (unit.kind()) {
            case MASS -> amount == null ? Optional.<Double>empty() : Optional.of((double) amount * unit.grams());
            case VOLUME -> amount == null ? Optional.<Double>empty() : Optional.of(amount * unit.millilitres() * WATER_DENSITY);
            case PINCH -> Optional.of(count(amount) * unit.grams());
            case COUNT, VAGUE -> Optional.<Double>empty();
        });
    }

    private static AmountInGrams volume(float amount, NutritionDataset.Unit unit, CatalogueFood food) {
        return millilitres(amount * unit.millilitres(), food);
    }

    private static AmountInGrams millilitres(double millilitres, CatalogueFood food) {
        var density = density(food);
        return density == null
                ? AmountInGrams.resolved(millilitres * WATER_DENSITY, EnumSet.of(LineFlag.VOLUME_WITHOUT_DENSITY))
                : AmountInGrams.resolved(millilitres * density, EnumSet.noneOf(LineFlag.class));
    }

    /** Order: owner's weight, food portion, scaled ordinary portion, typical container content. */
    private AmountInGrams pieces(float amount, NutritionDataset.Unit unit, CatalogueFood food, Map<String, Float> ownPortions) {
        var factor = unit.sizeOf() == null ? 1 : unit.factor();
        if (ownPortions.containsKey(unit.key())) {
            return AmountInGrams.byOwnPortion(amount * ownPortions.get(unit.key()));
        }
        if (unit.sizeOf() != null && ownPortions.containsKey(unit.sizeOf())) {
            return AmountInGrams.byOwnPortion(amount * factor * ownPortions.get(unit.sizeOf()));
        }
        var portion = portion(food, unit.key());
        if (portion.isPresent()) {
            return AmountInGrams.resolved(amount * portion.get().getGrams(), portionFlags(portion.get()));
        }
        var ordinaryUnit = unit.sizeOf() == null ? unit : unitLexicon.unit(unit.sizeOf()).orElseThrow();
        if (unit.sizeOf() != null) {
            var ordinary = portion(food, unit.sizeOf());
            if (ordinary.isPresent()) {
                var flags = portionFlags(ordinary.get());
                flags.add(LineFlag.SIZE_SCALED_PORTION);
                return AmountInGrams.resolved(amount * factor * ordinary.get().getGrams(), flags);
            }
        }
        return typicalContent(ordinaryUnit, food)
                .map(content -> {
                    var flags = EnumSet.of(LineFlag.TYPICAL_CONTAINER_SIZE);
                    flags.addAll(content.flags());
                    return AmountInGrams.resolved(amount * factor * content.grams(), flags);
                })
                .orElseGet(() -> AmountInGrams.unresolved(LineStatus.NO_PORTION));
    }

    private static Optional<AmountInGrams> typicalContent(NutritionDataset.Unit container, CatalogueFood food) {
        if (container.typicalGrams() != null) {
            return Optional.of(AmountInGrams.resolved(container.typicalGrams(), EnumSet.noneOf(LineFlag.class)));
        }
        if (container.typicalMillilitres() != null) {
            return Optional.of(millilitres(container.typicalMillilitres(), food));
        }
        return Optional.empty();
    }

    /** The food's density, else its base's. */
    private static Float density(CatalogueFood food) {
        return food.getDensityGPerMl() != null || food.getVariantOf() == null
                ? food.getDensityGPerMl() : food.getVariantOf().getDensityGPerMl();
    }

    private static Optional<CatalogueFoodPortion> portion(CatalogueFood food, String unitKey) {
        var own = food.getPortions().stream().filter(portion -> portion.getUnitKey().equals(unitKey)).findFirst();
        return own.isPresent() || food.getVariantOf() == null ? own : portion(food.getVariantOf(), unitKey);
    }

    private static EnumSet<LineFlag> portionFlags(CatalogueFoodPortion portion) {
        return portion.getOrigin() == CatalogueFoodPortion.Origin.ESTIMATED
                ? EnumSet.of(LineFlag.ESTIMATED_PORTION) : EnumSet.noneOf(LineFlag.class);
    }

    /** "Prise Salz" without an amount is one pinch. */
    private static double count(Float amount) {
        return amount == null ? 1 : amount;
    }
}
