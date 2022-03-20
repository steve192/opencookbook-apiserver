package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCreationRequest {
    private String emailAddress;
    private String password;

}