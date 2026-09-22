package com.sterul.opencookbookapiserver.controllers.requests;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.planning.Effort;
import com.sterul.opencookbookapiserver.entities.planning.MealSchedule;
import com.sterul.opencookbookapiserver.entities.planning.MealSlotSetting;
import com.sterul.opencookbookapiserver.entities.planning.PantryItem;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The weekplan wizard's answers. Ingredient ids that are not the caller's own are ignored when a
 * plan is made, so they cannot be used to learn anything about somebody else's ingredients.
 * Everything but the name and the meals may be left out, which keeps the profile's default.
 *
 * @param diet             a hard filter; null means no restriction
 * @param meatMealsPerWeek null means no limit; fish counts as meat
 * @param cooldownWeeks    weeks before a planned recipe may come round again
 * @param spreadVariety    keep the same main food off neighbouring days
 * @param includeHouseholdRecipes a personal plan also draws on household cookbooks; ignored for a household
 * @param meals            one entry per meal of the day that is planned at all
 */
public record PlanningProfileRequest(
        @NotBlank @Size(max = 255) String name,
        Boolean defaultProfile,
        @Min(1) @Max(20) Integer householdSize,
        Diet diet,
        @Min(0) @Max(35) Integer meatMealsPerWeek,
        @Positive Integer kcalPerDay,
        MacroStyle macroStyle,
        @Min(0) @Max(12) Integer cooldownWeeks,
        Boolean leftoversAllowed,
        Boolean spreadVariety,
        Boolean includeHouseholdRecipes,
        @NotEmpty List<@Valid Meal> meals,
        List<@Valid Pantry> pantry,
        @Size(max = 50) Set<Long> avoidedIngredientIds) {

    /**
     * @param days the weekdays it is cooked, each with how much work it may be; on the others it is a gap
     *             for the cook to fill
     */
    public record Meal(@NotNull MealType mealType, @NotNull Map<DayOfWeek, @NotNull Effort> days,
            @Positive Integer targetKcal) {

        public static Meal of(MealSlotSetting setting) {
            return new Meal(setting.getMealType(), setting.getSchedule().efforts(), setting.getTargetKcal());
        }

        MealSlotSetting toSetting() {
            return MealSlotSetting.builder().mealType(mealType).schedule(new MealSchedule(days)).targetKcal(targetKcal)
                    .build();
        }
    }

    /** @param amount null where the cook named it without saying how much */
    public record Pantry(@NotNull Long ingredientId, @Positive Float amount, @Size(max = 32) String unit) {

        public static Pantry of(PantryItem item) {
            return new Pantry(item.getIngredientId(), item.getAmount(), item.getUnit());
        }

        PantryItem toItem() {
            return PantryItem.builder().ingredientId(ingredientId).amount(amount).unit(unit).build();
        }
    }

    public PlanningProfile toProfile() {
        var profile = PlanningProfile.builder()
                .name(name.trim())
                .defaultProfile(Boolean.TRUE.equals(defaultProfile))
                .diet(diet)
                .meatMealsPerWeek(meatMealsPerWeek)
                .kcalPerDay(kcalPerDay)
                .macroStyle(macroStyle)
                .meals(distinctMeals())
                .pantry(new ArrayList<>(pantry == null ? List.of() : pantry.stream().map(Pantry::toItem).toList()))
                .avoidedIngredientIds(new HashSet<>(avoidedIngredientIds == null ? Set.of() : avoidedIngredientIds))
                .build();
        Optional.ofNullable(householdSize).ifPresent(profile::setHouseholdSize);
        Optional.ofNullable(cooldownWeeks).ifPresent(profile::setCooldownWeeks);
        Optional.ofNullable(leftoversAllowed).ifPresent(profile::setLeftoversAllowed);
        Optional.ofNullable(spreadVariety).ifPresent(profile::setSpreadVariety);
        Optional.ofNullable(includeHouseholdRecipes).ifPresent(profile::setIncludeHouseholdRecipes);
        return profile;
    }

    /** A meal named twice keeps its first answer; the wizard never sends one twice. */
    private List<MealSlotSetting> distinctMeals() {
        var seen = EnumSet.noneOf(MealType.class);
        return new ArrayList<>(meals.stream().filter(meal -> seen.add(meal.mealType())).map(Meal::toSetting).toList());
    }
}
