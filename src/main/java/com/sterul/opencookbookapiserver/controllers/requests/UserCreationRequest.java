package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

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