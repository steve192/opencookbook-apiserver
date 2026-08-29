package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserLoginRequest(
        @NotNull @NotBlank String emailAddress,
        @NotNull @NotBlank String password) {
}
