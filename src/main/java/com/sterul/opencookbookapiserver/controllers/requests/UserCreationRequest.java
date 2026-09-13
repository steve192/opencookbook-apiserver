package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreationRequest(
        // Checked here because this is where an address is stored, so nothing downstream - a
        // mail header, the log, the column - has to cope with whatever was sent.
        @NotNull @NotBlank @Email @Size(max = EmailAddresses.MAX_LENGTH) String emailAddress,
        @NotNull @NotBlank String password) {
}
