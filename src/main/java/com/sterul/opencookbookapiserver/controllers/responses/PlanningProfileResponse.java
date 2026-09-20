package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.controllers.requests.PlanningProfileRequest;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;

/** The answers in the same shape they are sent in, so the wizard can open a profile as it saved it. */
public record PlanningProfileResponse(Long id, String name, boolean defaultProfile, int householdSize,
        Diet diet, Integer meatMealsPerWeek, Integer kcalPerDay, MacroStyle macroStyle, int cooldownWeeks,
        boolean leftoversAllowed, boolean spreadVariety, List<PlanningProfileRequest.Meal> meals,
        List<PlanningProfileRequest.Pantry> pantry,
        Set<Long> avoidedIngredientIds) {

    public static PlanningProfileResponse fromEntity(PlanningProfile profile) {
        return new PlanningProfileResponse(profile.getId(), profile.getName(), profile.isDefaultProfile(),
                profile.getHouseholdSize(), profile.getDiet(), profile.getMeatMealsPerWeek(), profile.getKcalPerDay(),
                profile.getMacroStyle(), profile.getCooldownWeeks(), profile.isLeftoversAllowed(),
                profile.isSpreadVariety(), profile.getMeals().stream().map(PlanningProfileRequest.Meal::of).toList(),
                profile.getPantry().stream().map(PlanningProfileRequest.Pantry::of).toList(),
                Set.copyOf(profile.getAvoidedIngredientIds()));
    }
}
