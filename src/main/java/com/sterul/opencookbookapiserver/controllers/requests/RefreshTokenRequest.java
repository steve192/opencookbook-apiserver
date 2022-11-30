package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotNull
    @NotBlank
    private String refreshToken;
}
