package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class RefreshTokenRequest {

    @NotNull
    @NotBlank
    private String refreshToken;
}
