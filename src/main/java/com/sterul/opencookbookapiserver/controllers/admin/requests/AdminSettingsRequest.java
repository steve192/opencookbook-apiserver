package com.sterul.opencookbookapiserver.controllers.admin.requests;

import com.sterul.opencookbookapiserver.entities.instance.SignupMode;

import jakarta.validation.constraints.NotNull;

public record AdminSettingsRequest(@NotNull SignupMode signupMode) {
}
