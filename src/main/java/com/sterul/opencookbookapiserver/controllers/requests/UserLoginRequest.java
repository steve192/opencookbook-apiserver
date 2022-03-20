package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserLoginRequest {
    private String emailAddress;
    private String password;
}