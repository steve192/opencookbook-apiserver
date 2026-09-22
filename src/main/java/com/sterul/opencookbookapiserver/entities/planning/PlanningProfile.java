package com.sterul.opencookbookapiserver.entities.planning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.ScopedEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A cook's answers to the weekplan wizard, kept so that planning next week is one tap rather than
 * ten questions. A cook may keep several ("normal week", "low carb").
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class PlanningProfile extends AuditableEntity implements ScopedEntity {

    @Id
    @SequenceGenerator(name = "planning_profile_seq", sequenceName = "planning_profile_seq", allocationSize = 1)
    @GeneratedValue(generator = "planning_profile_seq")
    @EqualsAndHashCode.Include
    private Long id;

    /** Exactly one of these is set: a profile is a person's own or a household's. */
    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CookpalUser owner;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Household household;

    @Column(nullable = false)
    private String name;

    /** The one the wizard opens with. */
    private boolean defaultProfile;

    /** How many people eat; a planned meal is cooked for this many. */
    @Builder.Default
    private int householdSize = 2;

    /** A hard filter. Null means no restriction. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Diet diet;

    /** Soft; null means no limit. Fish counts as meat. */
    private Integer meatMealsPerWeek;

    /** Soft; null leaves calories out of planning. */
    private Integer kcalPerDay;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MacroStyle macroStyle;

    /** A recipe planned within this many weeks, in the draft or saved plans, is not suggested again. */
    @Builder.Default
    private int cooldownWeeks = 2;

    @Builder.Default
    private boolean leftoversAllowed = true;

    /** Keep the same main food and recipe group off neighbouring days. */
    @Builder.Default
    private boolean spreadVariety = true;

    /** A personal plan also draws on the household cookbooks its owner may read. */
    private boolean includeHouseholdRecipes;

    @ElementCollection
    @CollectionTable(name = "planning_profile_meal", joinColumns = @JoinColumn(name = "profile_id"))
    @Builder.Default
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<MealSlotSetting> meals = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "planning_profile_pantry", joinColumns = @JoinColumn(name = "profile_id"))
    @Builder.Default
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<PantryItem> pantry = new ArrayList<>();

    /** A hard filter, matched through the catalogue so an avoided food's variants are avoided too. */
    @ElementCollection
    @CollectionTable(name = "planning_profile_avoided", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "ingredient_id")
    @Builder.Default
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Long> avoidedIngredientIds = new HashSet<>();

    /**
     * How a meal is planned. A draft made before the profile was edited may hold a meal the profile
     * no longer mentions; that one is planned without constraints rather than failing.
     */
    public MealSlotSetting mealSetting(MealType mealType) {
        return meals.stream().filter(meal -> meal.getMealType() == mealType).findFirst()
                .orElseGet(() -> MealSlotSetting.builder().mealType(mealType).schedule(MealSchedule.everyDay()).build());
    }

    /**
     * The energy a meal should have per serving: its own target, or an even share of what the day
     * leaves after the meals that have one. Null where neither is set.
     */
    public Double kcalTargetOf(MealSlotSetting meal) {
        if (meal.getTargetKcal() != null) {
            return meal.getTargetKcal().doubleValue();
        }
        var withoutOwn = meals.stream().filter(planned -> planned.getTargetKcal() == null).count();
        if (kcalPerDay == null || withoutOwn == 0) {
            return null;
        }
        var owned = meals.stream().map(MealSlotSetting::getTargetKcal).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        return Math.max(0, kcalPerDay - owned) / (double) withoutOwn;
    }
}
