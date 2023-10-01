package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class RefreshTokenRequest {

    @NotNull
    @NotBlank
    private String refreshToken;
}
