package com.sterul.opencookbookapiserver.controllers.setup;

import com.sterul.opencookbookapiserver.controllers.requests.EmailAddresses;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Checked as a signup is. */
public record SetupRequest(
        @NotNull @NotBlank @Email @Size(max = EmailAddresses.MAX_LENGTH) String emailAddress,
        @NotNull @NotBlank String password) {
}
