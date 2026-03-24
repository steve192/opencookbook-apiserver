package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {
    @NotNull
    @NotBlank
    private String emailAddress;
    
    @NotNull
    @NotBlank
    private String password;
}
