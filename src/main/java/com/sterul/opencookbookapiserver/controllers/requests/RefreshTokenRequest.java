package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class RefreshTokenRequest {

    @NotNull
    @NotBlank
    private String refreshToken;

    /** Set by clients that store the refresh token in the answer; older apps leave it out and keep theirs. */
    private boolean rotate;
}
