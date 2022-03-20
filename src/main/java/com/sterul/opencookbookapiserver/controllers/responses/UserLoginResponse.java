package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserLoginResponse {

    private String token;
    private String refreshToken;
    private boolean userActive;

}