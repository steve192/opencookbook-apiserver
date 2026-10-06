package com.sterul.opencookbookapiserver.controllers.households.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HouseholdNameRequest(@NotBlank @Size(max = 64) String name) {
}
