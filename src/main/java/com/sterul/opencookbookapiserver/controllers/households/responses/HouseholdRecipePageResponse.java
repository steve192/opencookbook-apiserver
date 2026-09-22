package com.sterul.opencookbookapiserver.controllers.households.responses;

import java.util.List;

import org.springframework.data.domain.Slice;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/** @param last whether this is the final page */
public record HouseholdRecipePageResponse(List<HouseholdRecipeResponse> recipes, boolean last) {

    public static HouseholdRecipePageResponse of(Slice<Recipe> page, CookpalUser viewer) {
        return new HouseholdRecipePageResponse(
                page.getContent().stream().map(recipe -> HouseholdRecipeResponse.of(recipe, viewer)).toList(),
                page.isLast());
    }
}
