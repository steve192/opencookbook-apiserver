package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Data;

@Data
public class UserCreationRequest {
    private String emailAddress;
    private String password;

}