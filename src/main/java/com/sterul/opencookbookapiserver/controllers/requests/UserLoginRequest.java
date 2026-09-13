package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserLoginRequest(
        // Deliberately not checked for shape: an account made before that was required must
        // still be able to sign in, and a malformed address is already wrong credentials.
        @NotNull @NotBlank String emailAddress,
        @NotNull @NotBlank String password) {
}
