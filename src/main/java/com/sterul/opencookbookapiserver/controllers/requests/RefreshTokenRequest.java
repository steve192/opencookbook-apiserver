package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
