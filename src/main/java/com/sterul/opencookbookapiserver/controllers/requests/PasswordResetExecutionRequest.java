package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetExecutionRequest {
    private String newPassword;
    private String passwordResetId;
}
