package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
public class UserCreationRequest {
    @NotNull
    @NotBlank
    private String emailAddress;
    @NotNull
    @NotBlank
    private String password;

}