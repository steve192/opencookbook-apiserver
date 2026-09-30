package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.Data;

@Data
public class RefreshTokenResponse {
    private String token;

    /** The refresh token to use from now on; null unless the request asked to rotate. */
    private String refreshToken;
}
