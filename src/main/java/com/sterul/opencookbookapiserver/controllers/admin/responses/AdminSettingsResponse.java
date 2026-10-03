package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.entities.instance.SignupMode;

public record AdminSettingsResponse(SignupMode signupMode) {
}
