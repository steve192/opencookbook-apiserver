package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Builder
@Data
public class UserLoginRequest {
    @NotNull
    @NotBlank
    private String emailAddress;
    
    @NotNull
    @NotBlank
    private String password;
}