package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.Data;

@Data
public class UserLoginResponse {

    private String token;
    private String refreshToken;

}