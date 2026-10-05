package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.services.SignInService.IssuedTokens;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserLoginResponse {

    private String token;
    private String refreshToken;
    private boolean userActive;

    public static UserLoginResponse of(IssuedTokens tokens) {
        return builder().token(tokens.accessToken()).refreshToken(tokens.refreshToken()).userActive(true).build();
    }
}
