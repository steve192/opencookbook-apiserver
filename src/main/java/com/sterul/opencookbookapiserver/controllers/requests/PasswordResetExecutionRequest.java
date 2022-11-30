package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetExecutionRequest {
    @NotNull
    @NotBlank
    private String newPassword;
    @NotNull
    @NotBlank
    private String passwordResetId;
}
