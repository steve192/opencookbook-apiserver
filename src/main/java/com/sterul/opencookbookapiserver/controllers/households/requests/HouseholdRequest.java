package com.sterul.opencookbookapiserver.controllers.households.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HouseholdRequest(@NotBlank @Size(max = 64) String name, boolean shareRecipes) {
}
