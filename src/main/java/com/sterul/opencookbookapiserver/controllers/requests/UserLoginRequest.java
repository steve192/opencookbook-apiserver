package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserLoginRequest {
    @NotNull
    @NotBlank
    private String emailAddress;

    @NotNull
    @NotBlank
    private String password;
}